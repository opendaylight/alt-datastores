/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.impl;

import static org.testcontainers.containers.Network.newNetwork;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

public final class MongoContainer {
    public static MongoContainer INSTANCE = new MongoContainer();
    private static final int MONGO_PORT = 27017;
    private static final String MONGO_IMAGE = "mongo:4.0.8";

    private MongoClient mongoClient;
    private com.mongodb.reactivestreams.client.MongoClient rsMongoClient;
    private final String endpointURI;

    @SuppressWarnings("checkstyle:IllegalCatch")
    private MongoContainer() {
        Network network = newNetwork();

        GenericContainer m1 = new GenericContainer(MONGO_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("M1")
                .withExposedPorts(MONGO_PORT)
                .withCommand("--replSet rs0 --bind_ip localhost,M1");

        GenericContainer m2 = new GenericContainer(MONGO_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("M2")
                .withExposedPorts(MONGO_PORT)
                .withCommand("--replSet rs0 --bind_ip localhost,M2");

        GenericContainer m3 = new GenericContainer(MONGO_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("M3")
                .withExposedPorts(MONGO_PORT)
                .withCommand("--replSet rs0 --bind_ip localhost,M3");

        m1.start();
        m2.start();
        m3.start();

        try {
            m1.execInContainer("/bin/bash", "-c",
                    "mongo --eval 'printjson(rs.initiate({_id:\"rs0\","
                    + "members:[{_id:0,host:\"M1:27017\"},{_id:1,host:\"M2:27017\"},{_id:2,host:\"M3:27017\"}]}))' "
                    + "--quiet");
            m1.execInContainer("/bin/bash", "-c",
                    "until mongo --eval \"printjson(rs.isMaster())\" | grep ismaster | grep true > /dev/null 2>&1;"
                    + "do sleep 1;done");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initiate rs.", e);
        }

        endpointURI = "mongodb://" + m1.getContainerIpAddress() + ":" + m1.getFirstMappedPort();

        mongoClient = MongoClients
                .create(endpointURI);

        rsMongoClient = com.mongodb.reactivestreams.client.MongoClients
                .create(endpointURI);
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }

    public com.mongodb.reactivestreams.client.MongoClient getRSMongoClient() {
        return rsMongoClient;
    }
}
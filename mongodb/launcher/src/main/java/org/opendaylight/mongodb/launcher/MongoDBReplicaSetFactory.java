/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.mongodb.launcher;

import static java.util.stream.Collectors.toList;
import static org.opendaylight.mongodb.launcher.MongoDBContainer.MONGO_PORT;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;

public final class MongoDBReplicaSetFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoDBReplicaSetFactory.class);

    private MongoDBReplicaSetFactory() {

    }

    public static MongoDBReplicaSet buildReplicaSet(@NonNull String clusterName, int nodes, boolean ssl) {
        return buildReplicaSet(clusterName, nodes, ssl, false);
    }

    public static MongoDBReplicaSet buildReplicaSet(@NonNull String replicaSetName, int nodes, boolean ssl,
                                                    boolean restartable, String... additionalArgs) {
        final Network network = Network.builder().id(replicaSetName).build();
        final CountDownLatch latch = new CountDownLatch(nodes);
        final AtomicBoolean failedToStart = new AtomicBoolean(false);
        final MongoDBContainer.LifecycleListener listener = new MongoDBContainer.LifecycleListener() {
            @Override
            public void started(MongoDBContainer container) {
                latch.countDown();
            }

            @Override
            public void failedToStart(MongoDBContainer container, Exception exception) {
                LOGGER.error("Exception while starting mongodb container: ", exception);
                failedToStart.set(true);
                latch.countDown();
            }

            @Override
            public void stopped(MongoDBContainer container) {
            }
        };

        final List<String> endpoints = IntStream.range(0, nodes).mapToObj(i -> "mongo" + i).collect(toList());

        final List<MongoDBContainer> containers = endpoints.stream()
            .map(e -> new MongoDBContainer(network, listener, ssl, replicaSetName, e, endpoints, restartable,
                    additionalArgs))
            .collect(toList());

        return new MongoDBReplicaSet() {
            @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION",
                    justification = "It's OK.")
            @SuppressWarnings("checkstyle:IllegalCatch")
            @Override
            public void start() {
                List<String> endpoints = new ArrayList<>();
                int id = 0;
                for (MongoDBContainer container : containers) {
                    container.start();
                    endpoints.add("{_id:" + id + ",host:\"" + container.getEndpoint() + ":" + MONGO_PORT + "\"}");
                    id++;
                }

                try {
                    containers.get(0).getContainer().execInContainer("/bin/bash", "-c",
                        "mongo --eval 'printjson(rs.initiate({_id:\"" + replicaSetName + "\","
                                + "members:[" + String.join("," , endpoints) + "]}))' "
                                + "--quiet");
                    containers.get(0).getContainer().execInContainer("/bin/bash", "-c",
                        "until mongo --eval \"printjson(rs.isMaster())\" | grep ismaster | grep true > /dev/null 2>&1;"
                                + "do sleep 1;done");
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to initiate rs.", e);
                }
            }

            @Override
            public void close() {
                containers.forEach(MongoDBContainer::close);
            }

            @Override
            public void restart() {
                containers.forEach(MongoDBContainer::restart);
            }

            @Override
            public String getConnecionString() {
                return "mongodb://" + containers.get(0).getContainer().getContainerIpAddress() + ":"
                        + containers.get(0).getContainer().getMappedPort(MONGO_PORT);
            }
        };
    }
}

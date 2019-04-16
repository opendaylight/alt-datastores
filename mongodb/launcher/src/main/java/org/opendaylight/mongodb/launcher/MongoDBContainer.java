/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.mongodb.launcher;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.Network;

public class MongoDBContainer implements AutoCloseable {

    interface LifecycleListener {
        void started(MongoDBContainer container);

        void failedToStart(MongoDBContainer container, Exception exception);

        void stopped(MongoDBContainer container);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoDBContainer.class);

    private static final String MONGO_DOCKER_IMAGE_NAME = "mongo:4.0.8";
    public static final int MONGO_PORT = 27017;

    private final String endpoint;
    private final boolean ssl;
    private final FixedHostPortGenericContainer<?> container;
    private final LifecycleListener listener;

    public MongoDBContainer(Network network, LifecycleListener listener, boolean ssl, String replicaSetName,
            String endpoint, List<String> endpoints, boolean restartable) {
        this(network, listener, ssl, replicaSetName, endpoint, endpoints, restartable, emptyList());
    }

    public MongoDBContainer(Network network, LifecycleListener listener, boolean ssl, String replicaSetName,
            String endpoint, List<String> endpoints, boolean restartable, String... additionalArgs) {
        this(network, listener, ssl, replicaSetName, endpoint, endpoints, restartable, asList(additionalArgs));
    }

    public MongoDBContainer(Network network, LifecycleListener listener, boolean ssl, String replicaSetName,
                         String endpoint, List<String> endpoints, boolean restartable, List<String> additionalArgs) {
        this.endpoint = endpoint;
        this.ssl = ssl;
        this.listener = listener;

        final String name = endpoint;
        final List<String> command = new ArrayList<>();

        this.container = new FixedHostPortGenericContainer<>(MONGO_DOCKER_IMAGE_NAME);
        this.container.withExposedPorts(MONGO_PORT);
        this.container.withNetwork(network);
        this.container.withNetworkAliases(name);

        command.add("--replSet");
        command.add(replicaSetName);
        command.add("--bind_ip");
        command.add("localhost," + name);

        if (!command.isEmpty()) {
            this.container.withCommand(command.toArray(new String[command.size()]));
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void start() {
        LOGGER.debug("starting mongodb container {} with command: {}",
                endpoint, String.join(" ", container.getCommandParts()));

        try {
            this.container.start();
            this.listener.started(this);
        } catch (Exception exception) {
            this.listener.failedToStart(this, exception);
        }
    }

    public void restart() {
        LOGGER.debug("restarting mongodb container {} with command: {}",
                endpoint, String.join(" ", container.getCommandParts()));

        final int port = this.container.getMappedPort(MONGO_PORT);
        this.container.stop();
        this.container.withExposedPorts(MONGO_PORT);
        this.container.withFixedExposedPort(port, MONGO_PORT);
        this.container.start();
    }

    @Override
    public void close() {
        if (this.container != null) {
            this.container.stop();
        }
    }

    public FixedHostPortGenericContainer<?> getContainer() {
        return container;
    }

    public String getEndpoint() {
        return endpoint;
    }
}

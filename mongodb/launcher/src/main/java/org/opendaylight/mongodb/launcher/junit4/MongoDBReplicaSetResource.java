/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.mongodb.launcher.junit4;

import org.junit.rules.ExternalResource;
import org.opendaylight.mongodb.launcher.MongoDBReplicaSet;
import org.opendaylight.mongodb.launcher.MongoDBReplicaSetFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit4 ExternalResource to have mongodb replica set in tests.
 */
public class MongoDBReplicaSetResource extends ExternalResource implements MongoDBReplicaSet {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDBReplicaSetResource.class);

    private final MongoDBReplicaSet replicaSet;

    public MongoDBReplicaSetResource(String clusterName) {
        this(clusterName, 3, false);
    }

    public MongoDBReplicaSetResource(String clusterName, int nodes) {
        this(clusterName, nodes, false);
    }

    public MongoDBReplicaSetResource(String clusterName, int nodes, boolean ssl) {
        this(clusterName, nodes, ssl, false);
    }

    public MongoDBReplicaSetResource(String clusterName, int nodes, boolean ssl, boolean restartable) {
        this.replicaSet = MongoDBReplicaSetFactory.buildReplicaSet(clusterName, nodes, ssl, restartable);
    }

    // Test framework methods

    @Override
    protected void before() throws Throwable {
        this.replicaSet.start();
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected void after() {
        try {
            this.replicaSet.close();
        } catch (RuntimeException e) {
            LOG.warn("close() failed (but ignoring it)", e);
        }
    }

    // Relay replicaSet methods to replicaSet

    @Override
    public void start() {
        this.replicaSet.start();
    }

    @Override
    public void restart() {
        this.replicaSet.restart();
    }

    @Override
    public void close() {
        this.replicaSet.close();
    }

    @Override
    public String getConnecionString() {
        return this.replicaSet.getConnecionString();
    }
}

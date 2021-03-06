/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.mongodb.launcher;

import org.junit.Test;

/**
 * Tests (just) starting the {@link MongoDBReplicaSetStartTest}.
 *
 * @author Jie Han
 */
public class MongoDBReplicaSetStartTest {

    @Test
    public void testStartMongoDB() throws Exception {
        try (MongoDBReplicaSet rs = MongoDBReplicaSetFactory.buildReplicaSet("rs-test", 3, false)) {
            rs.start();
        }
    }
}

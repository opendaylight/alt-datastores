/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.impl;

import org.opendaylight.mongodb.launcher.MongoDBReplicaSet;
import org.opendaylight.mongodb.launcher.MongoDBReplicaSetFactory;

public final class Singleton {
    public static MongoDBReplicaSet INSTANCE =
            MongoDBReplicaSetFactory.buildReplicaSet("rs-test", 3, false);

    static {
        INSTANCE.start();
    }

    private Singleton() {

    }
}
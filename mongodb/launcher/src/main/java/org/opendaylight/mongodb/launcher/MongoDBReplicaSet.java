/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.mongodb.launcher;

public interface MongoDBReplicaSet extends AutoCloseable {

    void start();

    void restart();

    @Override
    void close();

    String getConnecionString();
}

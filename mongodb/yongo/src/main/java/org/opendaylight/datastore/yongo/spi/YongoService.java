/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.spi;

import com.google.common.util.concurrent.FluentFuture;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.datastore.yongo.impl.YangMongo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;

/**
 * The internal service for {@link YangMongo}.
 *
 * @author Jie Han
 */
public interface YongoService extends SchemaContextListener, AutoCloseable {

    @NonNull <T extends NormalizedNode<?, ?>> FluentFuture<Optional<T>>
    read(@NonNull ClientSession session, @NonNull LogicalDatastoreType type,
         @NonNull YangInstanceIdentifier yii);

    void put(@NonNull ClientSession session, @NonNull LogicalDatastoreType type,
             @NonNull YangInstanceIdentifier yii, @NonNull NormalizedNode<?, ?> data);

    void delete(@NonNull ClientSession session, @NonNull LogicalDatastoreType type,
                @NonNull YangInstanceIdentifier yii);

    void merge(@NonNull ClientSession session, @NonNull LogicalDatastoreType type,
                      @NonNull YangInstanceIdentifier yii, @NonNull NormalizedNode<?, ?> data);

    ClientSession startSession();

    MongoDatabase getDatabase(LogicalDatastoreType type);
}

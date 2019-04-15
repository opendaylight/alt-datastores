/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.transaction;

import com.google.common.util.concurrent.FluentFuture;
import com.mongodb.client.ClientSession;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.opendaylight.datastore.yongo.spi.YongoService;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * The definition of read/write transaction which implements {@link DOMDataTreeReadWriteTransaction}.
 *
 * @author Jie Han
 */
public class YongoReadWriteTransaction<T> extends YongoWriteOnlyTransaction<T>
        implements DOMDataTreeReadWriteTransaction {

    public YongoReadWriteTransaction(final T identifier, final ClientSession session,
                                     final YongoService yongoService,
                                     final Consumer<ClientSession> writeCommit,
                                     final Consumer<ClientSession> writeCancel) {
        super(identifier, session, yongoService, writeCommit, writeCancel);
    }

    @Override
    public FluentFuture<Optional<NormalizedNode<?, ?>>> read(LogicalDatastoreType store, YangInstanceIdentifier path) {
        return getYongoService().read(getSession(), store, path);
    }

    @Override
    public FluentFuture<Boolean> exists(LogicalDatastoreType store, YangInstanceIdentifier path) {
        final Optional<NormalizedNode<?, ?>> result;
        try {
            result = read(store, path).get();
        } catch (InterruptedException | ExecutionException ex) {
            return FluentFutures.immediateFluentFuture(false);
        }

        return FluentFutures.immediateFluentFuture(result.isPresent());
    }
}

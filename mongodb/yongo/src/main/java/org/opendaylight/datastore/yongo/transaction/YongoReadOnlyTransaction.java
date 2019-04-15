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
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * The definition of read transaction which implements {@link DOMDataTreeReadTransaction}.
 *
 * @author Jie Han
 */
public class YongoReadOnlyTransaction<T> extends AbstractYongoTransaction<T> implements DOMDataTreeReadTransaction {

    private final Consumer<ClientSession> readClose;

    public YongoReadOnlyTransaction(final T identifier, final ClientSession session,
                                    final YongoService yongoService,
                                    final Consumer<ClientSession> readClose) {
        super(identifier, session, yongoService);
        this.readClose = readClose;
    }

    @Override
    public void close() {
        readClose.accept(getSession());
    }

    @Override
    public FluentFuture<Optional<NormalizedNode<?, ?>>> read(LogicalDatastoreType store, YangInstanceIdentifier path) {
        FluentFuture<Optional<NormalizedNode<?, ?>>> future = getYongoService().read(getSession(), store, path);
        close();
        return future;
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

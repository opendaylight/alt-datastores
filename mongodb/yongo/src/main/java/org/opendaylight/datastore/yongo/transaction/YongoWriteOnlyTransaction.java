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
import java.util.function.Consumer;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.datastore.yongo.spi.YongoService;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * The definition of write transaction which implements {@link DOMDataTreeWriteTransaction}.
 *
 * @author Jie Han
 */
public class YongoWriteOnlyTransaction<T> extends AbstractYongoTransaction<T> implements DOMDataTreeWriteTransaction {
    private final Consumer<ClientSession> writeCommit;
    private final Consumer<ClientSession> writeCancel;

    public YongoWriteOnlyTransaction(final T identifier, final ClientSession session,
                                     final YongoService yongoService,
                                     final Consumer<ClientSession> writeCommit,
                                     final Consumer<ClientSession> writeCancel) {
        super(identifier, session, yongoService);
        this.writeCommit = writeCommit;
        this.writeCancel = writeCancel;
    }

    @Override
    public @NonNull FluentFuture<? extends CommitInfo> commit() {
        writeCommit.accept(getSession());
        return CommitInfo.emptyFluentFuture();
    }

    @Override
    public boolean cancel() {
        writeCancel.accept(getSession());
        return true;
    }

    @Override
    public void put(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data) {
        getYongoService().put(getSession(), store, path, data);
    }

    @Override
    public void merge(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data) {
        getYongoService().merge(getSession(), store, path, data);
    }

    @Override
    public void delete(LogicalDatastoreType store, YangInstanceIdentifier path) {
        getYongoService().delete(getSession(), store, path);
    }
}

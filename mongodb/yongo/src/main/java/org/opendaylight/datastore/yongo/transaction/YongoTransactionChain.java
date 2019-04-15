/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.transaction;

import com.mongodb.client.ClientSession;
import org.opendaylight.datastore.yongo.impl.YongoDataStore;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;

/**
 * The definition of transaction chain which implements {@link DOMTransactionChain}.
 *
 * @author Jie Han
 */
public class YongoTransactionChain implements DOMTransactionChain {
    private final YongoDataStore dataStore;
    private final ClientSession session;
    private final DOMTransactionChainListener listener;

    public YongoTransactionChain(final YongoDataStore dataStore, final DOMTransactionChainListener listener) {
        this.dataStore = dataStore;
        this.listener = listener;
        session = dataStore.getYongoService().startSession();
    }

    @Override
    public DOMDataTreeReadTransaction newReadOnlyTransaction() {
        getSession().startTransaction();
        return new YongoReadOnlyTransaction<>(getDataStore().nextIdentifier(), getSession(),
                getDataStore().getYongoService(), YongoTransactionChain::readClose);
    }

    @Override
    public DOMDataTreeWriteTransaction newWriteOnlyTransaction() {
        getSession().startTransaction();
        return new YongoWriteOnlyTransaction<>(getDataStore().nextIdentifier(), getSession(),
                getDataStore().getYongoService(),
                YongoTransactionChain::writeCommit,
                YongoTransactionChain::writeCancel);
    }

    @Override
    public DOMDataTreeReadWriteTransaction newReadWriteTransaction() {
        getSession().startTransaction();
        return new YongoReadWriteTransaction<>(getDataStore().nextIdentifier(), getSession(),
                getDataStore().getYongoService(),
                YongoTransactionChain::writeCommit,
                YongoTransactionChain::writeCancel);
    }

    @Override
    public void close() {
        getSession().close();
        listener.onTransactionChainSuccessful(this);
    }

    private YongoDataStore getDataStore() {
        return dataStore;
    }

    private ClientSession getSession() {
        return session;
    }

    private static void readClose(final ClientSession session) {
        txClose(session);
    }

    private static void writeCommit(final ClientSession session) {
        session.commitTransaction();
    }

    private static void writeCancel(final ClientSession session) {
        txClose(session);
    }

    private static void txClose(final ClientSession session) {
        session.abortTransaction();
    }
}

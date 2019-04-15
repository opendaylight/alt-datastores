/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.transaction;

import com.mongodb.client.ClientSession;
import org.opendaylight.datastore.yongo.spi.YongoService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeTransaction;

/**
 * The abstract class of all transactions.
 *
 * @author Jie Han
 */
public class AbstractYongoTransaction<T> implements DOMDataTreeTransaction {
    private final YongoService yongoService;
    private final ClientSession session;
    private final T identifier;

    public AbstractYongoTransaction(final T identifier, final ClientSession session,
                                    final YongoService yongoService) {
        this.session = session;
        this.identifier = identifier;
        this.yongoService = yongoService;
    }

    public YongoService getYongoService() {
        return yongoService;
    }

    public ClientSession getSession() {
        return session;
    }

    @Override
    public Object getIdentifier() {
        return identifier;
    }
}

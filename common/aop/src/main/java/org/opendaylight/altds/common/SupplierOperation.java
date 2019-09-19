/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.altds.common;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SupplierOperation<R> extends AbstractOperation<R, Object> {
    private static final Logger LOG = LoggerFactory.getLogger(SupplierOperation.class);
    private final Supplier<? extends R> op;
    private final Supplier<Void> rb;

    SupplierOperation(Supplier<? extends R> opFunc, Supplier<Void> rbFunc, String identifier) {
        super(identifier);
        this.op = opFunc;
        this.rb = rbFunc;
    }

    SupplierOperation(Supplier<? extends R> opFunc, Supplier<Void> rbFunc) {
        this(opFunc, rbFunc, null);
    }


    @Override
    public Supplier<R> getOpSupplier(Object input) {
        return () -> {
            LOG.info("{}-{} execute starts!", this.getClass().getSimpleName(), getIdentifier());
            R ret = op.get();
            LOG.info("{}-{} execute finish!, output: {}", this.getClass().getSimpleName(), getIdentifier(), ret);
            return ret;
        };
    }

    @Override
    public Supplier<Void> getRbSupplier(Object input) {
        return () -> {
            LOG.info("{}-{} rollback starts!", this.getClass().getSimpleName(), getIdentifier());
            return rb.get();
        };
    }
}

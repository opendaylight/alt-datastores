/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.altds.common;

import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ApplyOperation<R, T> extends AbstractOperation<R, T> {
    private static final Logger LOG = LoggerFactory.getLogger(ApplyOperation.class);
    private final Function<? super T,? extends R> op;
    private final Function<? super T, Void> rb;

    ApplyOperation(Function<? super T,? extends R> opFunc, Function<? super T, Void> rbFunc, String id) {
        super(id);
        this.op = opFunc;
        this.rb = rbFunc;
    }

    ApplyOperation(Function<? super T,? extends R> opFunc, Function<? super T, Void> rbFunc) {
        this(opFunc, rbFunc, null);
    }

    @Override
    public Supplier<R> getOpSupplier(T input) {
        return () -> {
            LOG.info("{}-{} execute starts, input: {}", this.getClass().getSimpleName(), getIdentifier(), input);
            R ret = op.apply(input);
            LOG.info("{}-{} execute finish, output: {}", this.getClass().getSimpleName(), getIdentifier(), ret);
            return ret;
        };
    }

    @Override
    public Supplier<Void> getRbSupplier(T input) {
        return () -> {
            LOG.info("{}-{} rollback starts!, input: {}", this.getClass().getSimpleName(), getIdentifier(), input);
            return rb.apply(input);
        };
    }
}

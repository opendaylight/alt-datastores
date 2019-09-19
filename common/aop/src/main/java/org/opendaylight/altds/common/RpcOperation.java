/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.altds.common;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RpcOperation<R> extends ApplyOperation<R, DataObject> {
    private static final Logger LOG = LoggerFactory.getLogger(RpcOperation.class);
    private RpcFunc<R> rpc;

    RpcOperation(RpcFunc opFunc, Function<DataObject, Void> rbFunc, String id) {
        super(null, rbFunc, id);
        this.rpc = opFunc;
    }

    @SuppressWarnings("checkstyle:avoidhidingcauseexception")
    @Override
    public Supplier<R> getOpSupplier(DataObject input) {
        return () -> {
            LOG.info("{}-{} execute starts, input: {}", this.getClass().getSimpleName(), getIdentifier(), input);
            RpcResult<R> result;
            try {
                result = rpc.apply(input).get();
                LOG.info("{}-{} execute finish, output: {}", this.getClass().getSimpleName(), getIdentifier(), result);
            } catch (InterruptedException e) {
                LOG.warn("Rpc operation interrupted exception", e);
                // Make sonarLint happy
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } catch (ExecutionException e) {
                LOG.warn("Rpc operation execution exception", e);
                throw new CompletionException(e.getCause());
            }

            return result.getResult();
        };
    }
}

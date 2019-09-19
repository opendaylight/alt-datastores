/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.altds.common;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

abstract class AbstractOperation<R, T> implements Operation<R, T> {

    private String identifier;
    protected CompletionStage<R> stage;
    private T rbInput;

    AbstractOperation(@Nullable String identifier) {
        this.identifier = identifier == null ? "default" : identifier ;
    }

    abstract Supplier<R> getOpSupplier(T input);

    abstract Supplier<Void> getRbSupplier(T input);

    protected String getIdentifier() {
        return identifier;
    }

    @Override
    public CompletionStage<R> execute(T input) {
        this.rbInput = input;
        stage = CompletableFuture.supplyAsync(getOpSupplier(input));
        return stage;
    }

    @Override
    public CompletionStage<Void> rollback() {
        return CompletableFuture.supplyAsync(getRbSupplier(this.rbInput));
    }

    @Override
    public Boolean isDone() {
        return stage != null && stage.toCompletableFuture().isDone();
    }
}

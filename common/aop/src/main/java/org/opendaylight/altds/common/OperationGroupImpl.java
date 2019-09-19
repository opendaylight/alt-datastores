/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.altds.common;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

final class OperationGroupImpl<R, T> implements OperationGroup<R, T> {
    static final CompletionStage<Boolean> DEFAULT_CONDITION = CompletableFuture.completedFuture(true);
    private final CompletableFuture head;
    private CompletionStage memberTail;
    private Function<T, Boolean> condition;
    private List<Operation<?, ?>> opList = new ArrayList<>();

    OperationGroupImpl() {
        super();
        head = new CompletableFuture<>();
        memberTail = head;
    }

    @Override
    public OperationGroup<R, T> follow(Operation op) {
        memberTail =  memberTail.thenComposeAsync(op::execute);
        opList.add(op);
        return this;
    }

    @Override
    public OperationGroup<R, T> cond(Function<T, Boolean> cond) {
        condition =  cond;
        return this;
    }

    @SuppressWarnings("checkstyle:illegalcatch")
    @Override
    public CompletionStage<R> execute(T input) {
        if (condition != null) {
            try {
                if (!condition.apply(input)) {
                    return CompletableFuture.completedFuture(null);
                }
            } catch (Exception e) {
                CompletableFuture<R> tmp = new CompletableFuture<>();
                tmp.completeExceptionally(e);
                return tmp;
            }
        }

        head.complete(input);
        return (CompletionStage<R>) memberTail;
    }

    @Override
    public CompletionStage<Void> rollback() {
        List<Operation<?, ?>> tmp = ImmutableList.copyOf(opList);
        tmp = Lists.reverse(tmp);
        return rollback(tmp);
    }

    @SuppressWarnings("checkstyle:parametername")
    private CompletionStage<Void> rollback(List<Operation<?, ?>> ops) {
        final CompletableFuture<Void> tmpHead = new CompletableFuture<>();
        CompletionStage<Void> tmpTail = tmpHead;
        for (Operation<?, ?> o : ops) {
            if (o.isDone()) {
                tmpTail = tmpTail.thenComposeAsync(r -> o.rollback()).exceptionally(t -> null);
            }
        }
        tmpHead.complete(null);
        return tmpTail;
    }

    @Override
    public Boolean isDone() {
        return memberTail.toCompletableFuture().isDone();
    }
}

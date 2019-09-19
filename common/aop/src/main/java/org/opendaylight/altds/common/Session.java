/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.altds.common;

import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.opendaylight.yangtools.concepts.Delegator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Session implements OperationGroup<List<Object>, Object> {
    private static final Logger LOG = LoggerFactory.getLogger(Session.class);

    private CompletionStage<List<Object>> stage;
    private List<Operation<?, Object>> opList =  new ArrayList<>();
    private Function<Object, Boolean> condition;

    private Session() {
        super();
    }

    static Session newSession() {
        return new Session();
    }

    @Override
    public  OperationGroup<List<Object>, Object> follow(Operation operation) {
        opList.add(operation);
        return this;
    }

    @Override
    public OperationGroup<List<Object>, Object> cond(Function<Object, Boolean> cond) {
        condition =  cond;
        return this;
    }

    @SuppressWarnings("checkstyle:illegalcatch")
    @Override
    public CompletionStage<List<Object>> execute(Object input) {
        LOG.info("session execute");

        if (condition != null) {
            try {
                if (!condition.apply(input)) {
                    return CompletableFuture.completedFuture(ImmutableList.of());
                }
            } catch (Exception e) {
                CompletableFuture<List<Object>> head = new CompletableFuture<>();
                head.completeExceptionally(e);
                return head;
            }
        }

        List<CompletableFuture<?>> futures = opList.stream()
                .map(o -> o.execute(input).toCompletableFuture()).collect(toList());
        stage = CompletableFuture.allOf(futures.toArray((new CompletableFuture[futures.size()])))
                .thenApply(r -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
        return stage;
    }

    @Override
    public CompletionStage<Void> rollback() {
        LOG.info("session rollback");
        return CompletableFuture.allOf(opList.stream().filter(Operation::isDone)
                .map(o -> o.rollback().toCompletableFuture()).toArray(CompletableFuture[]::new))
                .exceptionally(t -> null);
    }

    @Override
    public Boolean isDone() {
        return stage != null && stage.toCompletableFuture().isDone();
    }

    private static class Submission implements Future<List<Object>>, Delegator<Future<List<Object>>> {
        Future<List<Object>> future;

        Submission(Future<List<Object>> future) {
            this.future = future;
        }

        @Override
        public Future<List<Object>> getDelegate() {
            return this.future;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return getDelegate().cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return getDelegate().isCancelled();
        }

        @Override
        public boolean isDone() {
            return getDelegate().isDone();
        }

        @SuppressWarnings("checkstyle:avoidhidingcauseexception")
        @Override
        public List<Object> get() throws InterruptedException, ExecutionException {
            List<Object> result;
            try {
                result = getDelegate().get();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof OperationException) {
                    throw new ExecutionException(e.getCause().getCause());
                }

                throw e;
            }

            return result;
        }

        @SuppressWarnings("checkstyle:avoidhidingcauseexception")
        @Override
        public List<Object> get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
                TimeoutException {
            List<Object> result;
            try {
                result = getDelegate().get(timeout, unit);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof OperationException) {
                    throw new ExecutionException(e.getCause().getCause());
                }

                throw e;
            }

            return result;
        }
    }


    public Future<List<Object>> submit() {
        return submit(null);
    }

    @SuppressWarnings("checkstyle:parametername")
    public Future<List<Object>> submit(Object input) {
        LOG.info("session submit");
        Future<List<Object>> future = execute(input).whenCompleteAsync((r, t) -> {
            if (t != null) {
                LOG.info("session except, start rollback...", t);
                rollback().toCompletableFuture().join();
            }
            LOG.info("session complete");
        }).toCompletableFuture();
        return new Submission(future);
    }
}

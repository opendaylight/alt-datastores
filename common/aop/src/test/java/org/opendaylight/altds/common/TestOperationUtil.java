/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.altds.common;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TestOperationUtil {
    private static final Logger LOG = LoggerFactory.getLogger(TestOperationUtil.class);
    protected static final AtomicInteger OP_NUM = new AtomicInteger();

    private TestOperationUtil() {

    }

    static void reset() {
        OP_NUM.set(0);
    }


    static Operation newSupplierOp() {
        Integer num = OP_NUM.incrementAndGet();
        return newSupplierOp(num);
    }

    static Operation newSupplierOp(Integer num) {
        return OperationFactory.newSupplierOp(() -> get("execute", SupplierOperation.class, num),
            () -> {
                get("rollback", SupplierOperation.class, num);
                return null;
            },
            num.toString());
    }

    static Operation newSupplierExOp(Integer num) {
        return OperationFactory.newSupplierOp(() -> getEx("execute", SupplierOperation.class, num),
            () -> {
                get("rollback", SupplierOperation.class, num);
                return null;
            },
            num.toString());
    }

    static Operation newApplyOp() {
        Integer num = OP_NUM.incrementAndGet();
        return newApplyOp(num);
    }

    static Operation newApplyOp(Integer num) {
        return OperationFactory.newApplyOp(r -> get("execute", ApplyOperation.class, num),
            r -> {
                get("rollback", ApplyOperation.class, num);
                return null;
            },
            num.toString());
    }

    static Operation newApplyExOp() {
        Integer num = OP_NUM.incrementAndGet();
        return newApplyExOp(num);
    }

    static Operation newApplyExOp(Integer num) {
        return OperationFactory.newApplyOp(r -> getEx("execute", ApplyOperation.class, num),
            r -> {
                get("rollback", ApplyOperation.class, num);
                return null;
            },
            num.toString());
    }

    static Operation newApplyOpWithList(Integer num) {
        return OperationFactory.newApplyOp(r -> get("execute", ApplyOperation.class, num),
            r -> {
                get("rollback", ApplyOperation.class, num);
                return null;
            },
            num.toString());
    }

    static Operation newRpcOp(Integer num) {
        return OperationFactory.newRpcOp(r -> rpc("execute", RpcOperation.class, num),
            r -> {
                rpc("rollback", RpcOperation.class, num);
                return null;
            },
            num.toString());
    }

    static Operation newRpcExOp(Integer num) {
        return OperationFactory.newRpcOp(r -> rpcEx("execute", RpcOperation.class, num),
            r -> {
                rpc("rollback", RpcOperation.class, num);
                return null;
            },
            num.toString());
    }

    static Integer getEx(String method, Class<?> clazz, Integer num) {
        int random = sleepRandom();
        LOG.info("{}-{} {} except, consume {} milliseconds!", clazz.getSimpleName(), num,
                method, random);
        throw new OperationException(new TestException());
    }

    static Integer get(String method, Class<?> clazz, Integer num) {
        int random = sleepRandom();
        LOG.info("{}-{} {} finished, consume {} milliseconds!", clazz.getSimpleName(), num,
                method, random);
        return num;
    }

    static Future<Integer> getComplete(String method, Class<?> clazz, Integer num) {
        int random = sleepRandom();
        LOG.info("{}-{} {} finished, consume {} milliseconds!", clazz.getSimpleName(), num,
                method, random);
        return CompletableFuture.completedFuture(num);
    }

    static Future<RpcResult<TestRpcData>> rpc(String method, Class<?> clazz, Integer num) {
        return CompletableFuture.supplyAsync(() -> {
            int random = sleepRandom();
            LOG.info("{}-{} {} finished, consume {} milliseconds!", clazz.getSimpleName(), num,
                    method, random);
            return RpcResultBuilder.success(new TestRpcData(num)).build();
        });
    }

    static class TestException extends Exception {

    }

    static Future<RpcResult<TestRpcData>> rpcEx(String method, Class<?> clazz, Integer num) {
        SettableFuture<RpcResult<TestRpcData>> future = SettableFuture.create();
        int random = sleepRandom();
        LOG.info("{}-{} {} exception, consume {} milliseconds!", clazz.getSimpleName(), num,
                method, random);
        future.setException(new OperationException(new TestException()));
        return future;
    }

    static class TestRpcData implements DataObject {
        private Integer num;

        TestRpcData(Integer num) {
            this.num = num;
        }

        @Override
        public Class<? extends DataObject> getImplementedInterface() {
            return TestRpcData.class;
        }

        @Override
        public String toString() {
            return "TestRpcData: " + num;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }

            TestRpcData that = (TestRpcData) obj;
            return num.equals(that.num);
        }

        @Override
        public int hashCode() {
            return Objects.hash(num);
        }
    }

    static int sleepRandom() {
        int random = (int)(Math.random() * 500 + 1);
        try {
            TimeUnit.MILLISECONDS.sleep(random);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return random;
    }

    @SuppressWarnings("checkstyle:localvariablename")
    static Session s(Object... ops) {
        Session s = Session.newSession();
        Lists.newArrayList(ops).forEach(o -> {
            if (o instanceof Integer) {
                s.follow(os((Integer) o));
            } else if (o instanceof Operation) {
                s.follow((Operation) o);
            }
        });
        return s;
    }

    @SuppressWarnings("checkstyle:localvariablename")
    static OperationGroup g(Object... ops) {
        OperationGroup g = OperationFactory.newOpGroup();
        Lists.newArrayList(ops).forEach(o -> {
            if (o instanceof Integer) {
                g.follow(oa((Integer) o));
            } else if (o instanceof Operation) {
                g.follow((Operation) o);
            }
        });
        return g;
    }

    static Operation os(Integer num) {
        return newSupplierOp(num);
    }

    static Operation oa(Integer num) {
        return newApplyOp(num);
    }

    static Operation ol(Integer num) {
        return newApplyOpWithList(num);
    }

    static Operation or(Integer num) {
        return newRpcOp(num);
    }

    static Operation er(Integer num) {
        return newRpcExOp(num);
    }

    static Operation e(Integer num) {
        return newApplyExOp(num);
    }
}

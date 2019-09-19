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

import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.binding.DataObject;

public final class OperationFactory {

    private OperationFactory() {

    }

    public static Session newSession() {
        return Session.newSession();
    }

    public static OperationGroup newOpGroup() {
        return new OperationGroupImpl();
    }

    public static <U, V> Operation newApplyOp(Function<? super U,? extends V> opFunc) {
        return newApplyOp(opFunc, r -> null, null);
    }

    public static <U, V> Operation newApplyOp(Function<? super U,? extends V> opFunc, @Nullable String id) {
        return newApplyOp(opFunc, r -> null, id);
    }

    public static <U, V> Operation newApplyOp(Function<? super U,? extends V> opFunc,
                                             Function<? super U, Void> rbFunc) {
        return newApplyOp(opFunc, rbFunc, null);
    }

    public static <U, V> Operation newApplyOp(Function<? super U,? extends V> opFunc,
                                                         Function<? super U, Void> rbFunc, @Nullable String id) {
        return new ApplyOperation<>(opFunc, rbFunc, id);
    }

    public static <R> Operation newRpcOp(RpcFunc<R> opFunc) {
        return new RpcOperation(opFunc, r -> null, null);
    }

    public static <R> Operation newRpcOp(RpcFunc<R> opFunc, @Nullable String id) {
        return new RpcOperation(opFunc, r -> null, id);
    }

    public static <R> Operation newRpcOp(RpcFunc<R> opFunc, Function<DataObject, Void> rbFunc) {
        return new RpcOperation(opFunc, rbFunc, null);
    }

    public static <R> Operation newRpcOp(RpcFunc<R> opFunc, Function<DataObject, Void> rbFunc, String id) {
        return new RpcOperation(opFunc, rbFunc, id);
    }

    public static <T> Operation newSupplierOp(Supplier<? extends T> opFunc) {
        return newSupplierOp(opFunc, () -> null, null);
    }


    public static <T> Operation newSupplierOp(Supplier<? extends T> opFunc, @Nullable String id) {
        return newSupplierOp(opFunc, () -> null, id);
    }

    public static <T> Operation newSupplierOp(Supplier<? extends T> opFunc, Supplier<Void> rbFunc) {
        return newSupplierOp(opFunc, rbFunc, null);
    }

    public static <T> Operation newSupplierOp(Supplier<? extends T> opFunc, Supplier<Void> rbFunc,
                                          @Nullable String id) {
        return new SupplierOperation<T>(opFunc, rbFunc, id);
    }
}

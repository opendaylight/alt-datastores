/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.altds.common;

import java.util.function.Function;

public interface OperationGroup<R, T> extends Operation<R, T> {
    OperationGroup<R, T> follow(Operation operation);

    OperationGroup<R, T> cond(Function<T, Boolean> cond);
}

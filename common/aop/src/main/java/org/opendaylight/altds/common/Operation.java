/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.altds.common;

import java.util.concurrent.CompletionStage;

import org.eclipse.jdt.annotation.Nullable;

public interface Operation<R, T> {
    CompletionStage<R> execute(@Nullable T input);

    CompletionStage<Void> rollback();

    Boolean isDone();
}

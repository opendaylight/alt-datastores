/*
 * Copyright © 2019 Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.mongo.example.impl;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.ReadWriteTransaction;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.GetTopInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.GetTopOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.GetTopOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.OpendaylightMdsalBindingTestService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.PutTopInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.PutTopOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.PutTopOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.Top;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.TopBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

@Singleton
public class ExampleImpl implements OpendaylightMdsalBindingTestService {
    private final DataBroker dataBroker;

    @Inject
    public ExampleImpl(@Reference(filter = "type=yongo") DataBroker mongoDataBroker) {
        this.dataBroker = mongoDataBroker;
    }

    @Override
    public ListenableFuture<RpcResult<GetTopOutput>> getTop(GetTopInput input) {
        final SettableFuture<RpcResult<GetTopOutput>> futureResult = SettableFuture.create();

        ReadWriteTransaction tx = dataBroker.newReadWriteTransaction();
        tx.read(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Top.class))
                .addCallback(new FutureCallback<Optional<Top>>() {
                    @Override
                    public void onSuccess(@Nullable Optional<Top> top) {
                        futureResult.set(RpcResultBuilder.success(
                                new GetTopOutputBuilder().setTopLevelList(
                                        top.orElse(null).getTopLevelList()).build()).build());
                    }

                    @Override
                    public void onFailure(Throwable ex) {
                        futureResult.set(RpcResultBuilder.<GetTopOutput>failed()
                                .withError(RpcError.ErrorType.APPLICATION, "Unexpected error read ", ex).build());
                    }
                }, MoreExecutors.directExecutor());

        return futureResult;
    }

    @Override
    public ListenableFuture<RpcResult<PutTopOutput>> putTop(PutTopInput input) {
        final SettableFuture<RpcResult<PutTopOutput>> futureResult = SettableFuture.create();
        final Top top = new TopBuilder().setTopLevelList(input.getTopLevelList()).build();
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Top.class), top);
        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
                @Override
                public void onSuccess(@Nullable CommitInfo commitInfo) {
                    futureResult.set(RpcResultBuilder.<PutTopOutput>success(new PutTopOutputBuilder().build()).build());
                }

                @Override
                public void onFailure(Throwable ex) {
                    futureResult.set(RpcResultBuilder.<PutTopOutput>failed().withError(RpcError.ErrorType.APPLICATION,
                            "Unexpected error committing ", ex).build());
                }
            }, MoreExecutors.directExecutor());

        return futureResult;
    }
}

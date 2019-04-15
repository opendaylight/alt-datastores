/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.impl;

import static java.nio.charset.StandardCharsets.US_ASCII;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.io.Files;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.io.File;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.datastore.yongo.transaction.YongoReadOnlyTransaction;
import org.opendaylight.datastore.yongo.transaction.YongoReadWriteTransaction;
import org.opendaylight.datastore.yongo.transaction.YongoTransactionChain;
import org.opendaylight.datastore.yongo.transaction.YongoWriteOnlyTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataBrokerExtension;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;

/**
 * {@link DOMDataBroker} registered in the OSGi service registry.
 *
 * @author Jie Han
 */
@Singleton
public class YongoDOMDataBroker implements DOMDataBroker, AutoCloseable {

    private final YongoDataStore dataStore;
    private final YongoStream yongoStream;
    private final ClassToInstanceMap<DOMDataBrokerExtension> extensions;

    @Inject
    public YongoDOMDataBroker(@Reference DOMSchemaService schemaService) throws Exception {
        this(schemaService, MongoClients.create(Files.readFirstLine(
            new File("../../yongo-launcher-maven-plugin/endpoint").getAbsoluteFile(), US_ASCII)),
            com.mongodb.reactivestreams.client.MongoClients.create(Files.readFirstLine(
                    new File("../../yongo-launcher-maven-plugin/endpoint").getAbsoluteFile(), US_ASCII)));
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @VisibleForTesting
    public YongoDOMDataBroker(@Reference DOMSchemaService schemaService,
                              @Reference MongoClient mongoClient,
                              @Reference com.mongodb.reactivestreams.client.MongoClient rsMongoClient) {
        this.dataStore = new YongoDataStore(schemaService, mongoClient);

        this.yongoStream = new YongoStream(rsMongoClient,
                dataStore.getYangJson(), schemaService);

        this.extensions = ImmutableClassToInstanceMap.of(
                DOMDataTreeChangeService.class, yongoStream);
    }

    public YongoDataStore getDataStore() {
        return dataStore;
    }

    @Override
    public void close() throws Exception {
        dataStore.close();
    }

    @Override
    public DOMTransactionChain createTransactionChain(DOMTransactionChainListener listener) {
        return new YongoTransactionChain(getDataStore(), listener);
    }

    @Override
    public @NonNull ClassToInstanceMap<DOMDataBrokerExtension> getExtensions() {
        return this.extensions;
    }

    @Override
    public DOMDataTreeReadTransaction newReadOnlyTransaction() {
        ClientSession session = getDataStore().getYongoService().startSession();
        session.startTransaction();
        return new YongoReadOnlyTransaction<>(getDataStore().nextIdentifier(), session,
                getDataStore().getYongoService(), YongoDOMDataBroker::readClose);
    }

    @Override
    public DOMDataTreeWriteTransaction newWriteOnlyTransaction() {
        ClientSession session = getDataStore().getYongoService().startSession();
        session.startTransaction();
        return new YongoWriteOnlyTransaction<>(getDataStore().nextIdentifier(), session,
                getDataStore().getYongoService(),
                YongoDOMDataBroker::writeCommit,
                YongoDOMDataBroker::writeCancel);
    }

    @Override
    public DOMDataTreeReadWriteTransaction newReadWriteTransaction() {
        ClientSession session = getDataStore().getYongoService().startSession();
        session.startTransaction();
        return new YongoReadWriteTransaction<>(getDataStore().nextIdentifier(), session,
                getDataStore().getYongoService(),
                YongoDOMDataBroker::writeCommit,
                YongoDOMDataBroker::writeCancel);
    }

    private static void readClose(final ClientSession session) {
        session.close();
    }

    private static void writeCommit(final ClientSession session) {
        session.commitTransaction();
        session.close();
    }

    private static void writeCancel(final ClientSession session) {
        session.abortTransaction();
        session.close();
    }
}
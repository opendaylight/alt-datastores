/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.common.util.concurrent.MoreExecutors;
import com.mongodb.MongoNamespace;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.client.model.changestream.OperationType;
import com.mongodb.reactivestreams.client.ChangeStreamPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bson.BsonDocument;
import org.bson.Document;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.datastore.yongo.publisher.copypaste.InMemoryDOMStoreTreeChangePublisher;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeListener;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.store.DOMStoreTreeChangePublisher;
import org.opendaylight.mdsal.dom.store.inmemory.InMemoryDOMDataStoreConfigProperties;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidate;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidates;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides data tree change notification service by using change streams of MongoDB.
 *
 * @author Jie Han
 */
public class YongoStream extends AbstractYongoBase<MongoClient, MongoCollection<Document>, MongoDatabase>
        implements DOMDataTreeChangeService {
    private static final Logger LOG = LoggerFactory.getLogger(YongoStream.class);
    private static String NUM_REGEX = "\\d+(?:[.,])*\\s*";
    private static String FIELD_SPLITTER = "\\.";
    private static String MODULE_SPLITTER = ":";
    private static String REVISION_SPLITTER = "@";
    private final Table<LogicalDatastoreType, String, ChangeStreamPublisher<Document>> publishers =
            HashBasedTable.create();
    private final Map<LogicalDatastoreType, InMemoryDOMStoreTreeChangePublisher> changePublishers = new HashMap<>();
    private final YangJson yangJson;

    public YongoStream(final MongoClient rsclient, final YangJson yangJson, final DOMSchemaService domSchemaService) {
        super(rsclient, domSchemaService);
        changePublishers.put(LogicalDatastoreType.CONFIGURATION,
                new InMemoryDOMStoreTreeChangePublisher(MoreExecutors.newDirectExecutorService(),
                        InMemoryDOMDataStoreConfigProperties.DEFAULT_MAX_DATA_CHANGE_LISTENER_QUEUE_SIZE));
        changePublishers.put(LogicalDatastoreType.OPERATIONAL,
                new InMemoryDOMStoreTreeChangePublisher(MoreExecutors.newDirectExecutorService(),
                        InMemoryDOMDataStoreConfigProperties.DEFAULT_MAX_DATA_CHANGE_LISTENER_QUEUE_SIZE));
        this.yangJson = yangJson;
    }

    @Override
    public @NonNull <L extends DOMDataTreeChangeListener> ListenerRegistration<L>
    registerDataTreeChangeListener(@NonNull DOMDataTreeIdentifier treeId, @NonNull L listener) {
        subscribeChangeStream(treeId);
        return getChangePublishers(treeId).registerTreeChangeListener(treeId.getRootIdentifier(), listener);
    }

    private DOMStoreTreeChangePublisher getChangePublishers(final DOMDataTreeIdentifier treeId) {
        return changePublishers.get(treeId.getDatastoreType());
    }

    private void subscribeChangeStream(final DOMDataTreeIdentifier treeId) {
        final ChangeStreamPublisher<Document> publisher = generatePublisher(treeId);
        final ObservableSubscriber subscriber = new ObservableSubscriber(
                changePublishers.get(treeId.getDatastoreType()), this.yangJson);
        publisher.subscribe(subscriber);
    }

    private ChangeStreamPublisher<Document> generatePublisher(final DOMDataTreeIdentifier treeId) {
        if (this.publishers.contains(treeId.getDatastoreType(), getCollectionName(treeId.getRootIdentifier()))) {
            return this.publishers.get(treeId.getDatastoreType(), getCollectionName(treeId.getRootIdentifier()));
        }

        //TODO: Watch with pipeline to speedup without full document, but there are some issues:
        // 1. MongoDB does not provide deleted array elements by a pull operation,
        //    then how to publish 'delete' events with deleted data to data tree change listeners?
        // 2. Projection of pipeline is not supported by MongoDB java reactivestreams driver right now.
        final ChangeStreamPublisher<Document> publisher =
                getCollection(treeId).watch().fullDocument(FullDocument.UPDATE_LOOKUP);
        this.publishers.put(treeId.getDatastoreType(), getCollectionName(treeId.getRootIdentifier()), publisher);
        return publisher;
    }

    public MongoDatabase getDatabase(final LogicalDatastoreType type) {
        return getDatabases().computeIfAbsent(type,
            t -> getMongoClient().getDatabase(t.name().toLowerCase(Locale.ENGLISH)));
    }

    public MongoCollection<Document> getCollection(final DOMDataTreeIdentifier treeId) {
        return getCollection(treeId.getDatastoreType(), treeId.getRootIdentifier());
    }

    public MongoCollection<Document> getCollection(final LogicalDatastoreType type, final YangInstanceIdentifier yii) {
        return getDatabase(type).getCollection(getCollectionName(yii));
    }

    private static class ObservableSubscriber implements Subscriber<ChangeStreamDocument<Document>> {
        private volatile Subscription subscription;
        private final InMemoryDOMStoreTreeChangePublisher publisher;
        private final YangJson yangJson;

        ObservableSubscriber(final InMemoryDOMStoreTreeChangePublisher publisher, final YangJson yangJson) {
            this.publisher = publisher;
            this.yangJson = yangJson;
        }

        @Override
        public void onSubscribe(final Subscription sub) {
            subscription = sub;
            subscription.request(Integer.MAX_VALUE);
        }

        @Override
        public void onNext(final ChangeStreamDocument<Document> doc) {
            requireNonNull(doc);
            final Document full = doc.getFullDocument();
            if (full == null) {
                return;
            }

            if (doc.getOperationType().equals(OperationType.INSERT)
                    || doc.getOperationType().equals(OperationType.UPDATE)) {
                //TODO: Need more hard work to be consistent with IMDS's publish mechanism. Properly MongoDB should
                // support to notify deleted data first, or we have to replace {@link DataTreeChangeService} with new
                // change streams that would be developed in future.
                final YangInstanceIdentifier yii = getTopYII(doc);
                final SchemaPath topPath = SchemaPath.create(true, yii.getLastPathArgument().getNodeType());

                NormalizedNode<?, ?> node = yangJson.jsonStreamToNormalizedNode(topPath, full.toJson());

                DataTreeCandidate candidate = DataTreeCandidates.fromNormalizedNode(yii, node);
                publisher.publishChange(candidate);
            }
        }

        @Override
        public void onError(final Throwable throwable) {
            LOG.error("error {}", throwable.getMessage());
            onComplete();
        }

        @Override
        public void onComplete() {
            //TODO: resume after a completion and make waiting time longer for performance as we should listen all the
            // time until all listeners are removed.
            LOG.info("complete");
        }

        private YangInstanceIdentifier getTopYII(ChangeStreamDocument<Document> doc) {
            final Document full = doc.getFullDocument();
            if (full == null) {
                return null;
            }

            String collectionName = getCollectionName(doc);
            String[] uriRevision = collectionName.split(REVISION_SPLITTER);
            Set<String> keys = full.keySet();
            keys.remove("_id");
            String top = Iterators.getOnlyElement(keys.iterator());
            QName topName = QName.create(uriRevision[0],uriRevision[1], top.split(MODULE_SPLITTER)[1]);
            YangInstanceIdentifier yii = YangInstanceIdentifier.of(topName);
            return yii;
        }

        @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
                justification = "Keep this method for using in future")
        private SchemaPath getUpdatedSchemaPath(final ChangeStreamDocument<Document> doc) {
            requireNonNull(doc);
            BsonDocument update = doc.getUpdateDescription().getUpdatedFields();
            if (update == null) {
                return null;
            }

            final String field = update.getFirstKey();
            String[] fields = requireNonNull(field)
                    .replaceAll(NUM_REGEX, "").split(FIELD_SPLITTER);
            fields[0] = fields[0].split(FIELD_SPLITTER)[1];
            final List<QName> qnames = new ArrayList<>();
            String collectionName = getCollectionName(doc);
            String[] uriRevision = collectionName.split(REVISION_SPLITTER);
            Lists.newArrayList(fields).forEach(f -> qnames.add(QName.create(uriRevision[0], uriRevision[1], f)));
            return SchemaPath.create(qnames, true);
        }

        private String getCollectionName(final ChangeStreamDocument<Document> doc) {
            requireNonNull(doc);
            final MongoNamespace namespace = doc.getNamespace();
            if (namespace == null) {
                return null;
            }

            return namespace.getCollectionName();
        }
    }
}
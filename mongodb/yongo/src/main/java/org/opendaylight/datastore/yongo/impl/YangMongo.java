/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FluentFuture;
import com.mongodb.ClientSessionOptions;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.UpdateOptions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.jcip.annotations.ThreadSafe;
import org.bson.Document;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.datastore.yongo.spi.BsonResult;
import org.opendaylight.datastore.yongo.spi.UpdateBsonResult;
import org.opendaylight.datastore.yongo.spi.YongoService;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides CRUD operations for yang normalized data.
 *
 * @author Jie Han
 */
@ThreadSafe
public final class YangMongo extends AbstractYongoBase<MongoClient, MongoCollection, MongoDatabase>
        implements YongoService {
    private static final Logger LOG = LoggerFactory.getLogger(YangMongo.class);
    private final YangJson yangJson;
    private final Map<LogicalDatastoreType, MongoDatabase> databases = new HashMap<>();

    private YangMongo(final MongoClient mongoClient, final YangJson yangJson, final DOMSchemaService domSchemaService) {
        super(mongoClient, domSchemaService);
        this.yangJson = yangJson;
        final ClientSession session = startSession();
        // Drop old operational data in db when the system starts/restarts.
        getDatabase(LogicalDatastoreType.OPERATIONAL).drop(session);
        // Note: we should initialize all collections since mongodb does not allow to create new collection
        // in transactions, refer "https://docs.mongodb.com/manual/core/transactions/#restricted-operations"
        initCollections(session, domSchemaService.getGlobalContext());
    }

    @Override
    public @NonNull <T extends NormalizedNode<?, ?>> FluentFuture<Optional<T>>
    read(@NonNull final ClientSession session, @NonNull final LogicalDatastoreType type,
         @NonNull final YangInstanceIdentifier yii) {
        final MongoCollection<Document> collection = getCollection(type, yii);
        final BsonResult bsonResult = YangBson.parseStageRead(getSchemaContext(), yii);
        final AggregateIterable<Document> result =
            collection.aggregate(session, bsonResult.getBsons());
        if (result.iterator().hasNext()) {
            final Document doc = result.iterator().next();
            // If you specify an inclusion of a field that does not exist in the document,
            // $project ignores that field inclusion and does not add the field to the document (return "{}").
            // see https://docs.mongodb.com/manual/reference/operator/aggregation/project/#include-existing-fields
            if (doc.size() > 0) {
                final NormalizedNode<?, ?> node = yangJson.jsonStreamToNormalizedNode(yii, doc.toJson());
                return FluentFutures.immediateFluentFuture(Optional.of((T) node));
            }
        }

        return FluentFutures.immediateFluentFuture(Optional.empty());
    }

    @Override
    public void put(@NonNull final ClientSession session, @NonNull final LogicalDatastoreType type,
                    @NonNull final YangInstanceIdentifier yii, final @NonNull NormalizedNode<?, ?> data) {
        final String jsonStream = yangJson.normalizedNodeToJsonStream(yii, data);
        if (jsonStream != null) {
            final MongoCollection<Document> collection = getCollection(type, yii);
            Document doc = Document.parse(jsonStream);
            if (!YangJson.isListElement(yii)) {
                doc = (Document) Iterators.getOnlyElement(doc.values().iterator());
            }
            put(collection, session, yii, doc);
        }
    }

    @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE",
            justification = "I know what i am doing")
    private void put(final MongoCollection<Document> collection, final ClientSession session,
                     final YangInstanceIdentifier yii, final Document value) {
        //TODO: MongoDB can't do upsert an array element by one-call, pull then push.
//        if (YangJson.isListElement(yii)) {
//            updateDelete(session, collection, yii);
//        }

        final UpdateBsonResult parsedResult =
                YangBson.parseUpdateAdd(getSchemaContext(), yii, value);
        collection.updateOne(session, new Document(), parsedResult.getUpdate(), new UpdateOptions()
                .arrayFilters(parsedResult.getBsons())
                .upsert(true));
    }

    @Override
    public void delete(@NonNull final ClientSession session, @NonNull final LogicalDatastoreType type,
                       @NonNull final YangInstanceIdentifier yii) {
        final MongoCollection<Document> collection = getCollection(type, yii);
        updateDelete(session, collection, yii);
    }

    @Override
    public void merge(@NonNull final ClientSession session, @NonNull final LogicalDatastoreType type,
                      @NonNull final YangInstanceIdentifier yii, final @NonNull NormalizedNode<?, ?> data) {
        final MongoCollection<Document> collection = getCollection(type, yii);
        String jsonStream;
        try {
            jsonStream = yangJson.normalizedNodeToJsonStream(yii, data);
        } catch (IllegalStateException e) {
            LOG.warn("normalizedNodeToJsonStream failed.", e);
            return;
        }

        if (jsonStream != null) {

            final Document toMerge = Document.parse(jsonStream);

            final BsonResult bsonResult = YangBson.parseStageRead(getSchemaContext(), yii);
            final AggregateIterable<Document> result =
                    collection.aggregate(session, bsonResult.getBsons());
            if (result.iterator().hasNext()) {
                Document doc = result.iterator().next();
                mergeDocuments(yii, doc, toMerge);
                put(collection, session, yii, (Document) Iterators.getOnlyElement(doc.values().iterator()));
            }
        }
    }

    private void updateDelete(final ClientSession session, final MongoCollection<Document> collection,
                              final YangInstanceIdentifier yii) {
        final UpdateBsonResult parsedResult =
                YangBson.parseUpdateDel(getDomSchemaService().getGlobalContext(), yii);
        collection.updateOne(session, new Document(), parsedResult.getUpdate(), new UpdateOptions()
                .arrayFilters(parsedResult.getBsons())
                .upsert(true));
    }

    private SchemaPath getSchemaPath(final YangInstanceIdentifier yii) {
        SchemaPath path = SchemaPath.ROOT;
        for (PathArgument pathArgument : yii.getPathArguments()) {
            if (! (pathArgument instanceof NodeIdentifierWithPredicates)) {
                path = path.createChild(pathArgument.getNodeType());
            }
        }
        return path;
    }

    private SchemaNode getSchemaNode(final YangInstanceIdentifier yii) {
        return SchemaContextUtil.findDataSchemaNode(getSchemaContext(), getSchemaPath(yii));
    }

    private void mergeDocuments(final YangInstanceIdentifier yii, final Document doc, final Document toMerge) {
        final Document to;
        final Document from;
        to = (Document) Iterators.getOnlyElement(doc.values().iterator());
        if (YangJson.isTopLevel(yii)) {
            from = (Document) Iterators.getOnlyElement(toMerge.values().iterator());
        } else {
            from = toMerge;
        }

        mergeDocuments(getSchemaNode(yii), to, from);
    }

    private void mergeDocuments(final SchemaNode node, final Document doc1, final Document doc2) {
        requireNonNull(node);
        requireNonNull(doc1);
        requireNonNull(doc2);
        doc2.keySet().forEach(k -> {
            Object var1 = doc1.get(k);
            //TODO: augmented data nodes
            Optional<DataSchemaNode> child = ((DataNodeContainer) node).findDataChildByName(
                    QName.create(node.getQName(), k));
            if (!child.isPresent()) {
                child = tryToFindInChoiceNode(((DataNodeContainer) node), k);
            }
            Preconditions.checkArgument(child.isPresent());
            if (var1 instanceof Document) {
                mergeDocuments(child.get(), (Document) var1, (Document) doc2.get(k));
            } else if (var1 instanceof ArrayList) {
                Preconditions.checkArgument(child.get() instanceof ListSchemaNode);
                mergeArray((ListSchemaNode) child.get(), (ArrayList<Document>) var1, (ArrayList<Document>) doc2.get(k));
            } else {
                doc1.append(k, doc2.get(k));
            }
        });
    }

    private Optional<DataSchemaNode> tryToFindInChoiceNode(final DataNodeContainer node, final String field) {
        for (SchemaNode child : node.getChildNodes()) {
            if (child instanceof ChoiceSchemaNode) {
                for (CaseSchemaNode caseNode : ((ChoiceSchemaNode) child).getCases().values()) {
                    for (DataSchemaNode caseChild : caseNode.getChildNodes()) {
                        if (caseChild.getQName().getLocalName().equals(field)) {
                            return Optional.of(caseChild);
                        }
                    }
                }
            }
        }

        throw new IllegalArgumentException(String.format("Can not find data schema node %s.", field));
    }

    private void mergeArray(final ListSchemaNode node, final ArrayList<Document> arrayList1,
                            final ArrayList<Document> arrayList2) {
        arrayList2.forEach(toMerge -> {
            Document doc = containDocument(arrayList1, toMerge, node.getKeyDefinition());
            if (doc != null) {
                mergeDocuments(node, doc, toMerge);
            } else {
                arrayList1.add(toMerge);
            }
        });
    }

    private Document containDocument(final ArrayList<Document> arrayList1, final Document doc,
                                    final List<QName> keyDef) {
        for (Document doc1 : arrayList1) {
            if (equalsKey(doc1, doc, keyDef)) {
                return doc1;
            }
        }

        return null;
    }

    private boolean equalsKey(final Document doc1, final Document doc2, final List<QName> keyDef) {
        for (QName qname : keyDef) {
            if (!Objects.equals(doc1.get(qname.getLocalName()), doc2.get(qname.getLocalName()))) {
                return false;
            }
        }

        return true;
    }

    @VisibleForTesting
    @Override
    public MongoDatabase getDatabase(final LogicalDatastoreType type) {
        return databases.computeIfAbsent(type, t -> getMongoClient().getDatabase(t.name().toLowerCase(Locale.ENGLISH)));
    }

    @Override
    public void close() {
        //
    }

    @Override
    public ClientSession startSession() {
        return getMongoClient().startSession(ClientSessionOptions.builder()
                .defaultTransactionOptions(
                        TransactionOptions.builder().readPreference(ReadPreference.primary()).build()).build());
    }

    public MongoCollection<Document> getCollection(final LogicalDatastoreType type, final YangInstanceIdentifier yii) {
        return getDatabase(type).getCollection(getCollectionName(yii));
    }

    public static YongoService newInstance(final MongoClient mongo, final YangJson yangJson,
                                           final DOMSchemaService domSchemaService) {
        return new YangMongo(mongo, yangJson, domSchemaService);
    }

    @Override
    public void onGlobalContextUpdated(final SchemaContext context) {
        initCollections(startSession(), context);
    }

    private void initCollections(final ClientSession session, final SchemaContext context) {
        final MongoDatabase confDB = getDatabase(LogicalDatastoreType.CONFIGURATION);
        final MongoDatabase operDB = getDatabase(LogicalDatastoreType.OPERATIONAL);
        final MongoIterable<String> confCollections = confDB.listCollectionNames(session);
        final MongoIterable<String> operCollections = operDB.listCollectionNames(session);
        final List<String> conf = Lists.newArrayList(confCollections);
        final List<String> oper = Lists.newArrayList(operCollections);
        context.getModules().forEach(m -> {
            final String name = getCollectionName(m);
            if (!conf.contains(name)) {
                confDB.createCollection(session, name);
            }

            if (!oper.contains(name)) {
                operDB.createCollection(session, name);
            }
        });

        session.close();
    }
}

/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.impl;

import static java.util.Objects.requireNonNull;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jcip.annotations.ThreadSafe;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * The super class of {@link YangMongo} and {@link YongoStream} provides base common
 * utility methods.
 *
 * @author Jie Han
 */
@ThreadSafe
public abstract class AbstractYongoBase<M, C, D> implements AutoCloseable {
    private static String ITEM = "item";
    private final DOMSchemaService domSchemaService;
    private final M mongoClient;
    private final Map<LogicalDatastoreType, D> databases = new HashMap<>();

    AbstractYongoBase(final M mongoClient, final DOMSchemaService domSchemaService) {
        this.domSchemaService = domSchemaService;
        this.mongoClient = mongoClient;
    }

    public abstract D getDatabase(LogicalDatastoreType type);

    public abstract C getCollection(LogicalDatastoreType type, YangInstanceIdentifier yii);

    protected Map<LogicalDatastoreType, D> getDatabases() {
        return databases;
    }

    protected SchemaContext getSchemaContext() {
        return domSchemaService.getGlobalContext();
    }

    public M getMongoClient() {
        return mongoClient;
    }

    public static String getTopLevelFieldName(final SchemaContext schemaContext, final PathArgument topLevel) {
        final Module module = schemaContext.findModule(topLevel.getNodeType().getModule()).get();
        requireNonNull(module);
        return  module.getName() + ":" + topLevel.getNodeType().getLocalName();
    }

    public static Bson unwindState(final String fieldName) {
        return Aggregates.unwind("$" + fieldName);
    }

    public static Bson matchState(final String fieldName, final NodeIdentifierWithPredicates identifier) {
        final List<Bson> filters = new ArrayList<>();
        identifier.getKeyValues().forEach((k, v) -> {
            filters.add(Filters.eq(fieldName(fieldName, k.getLocalName()), v));
        });

        return Aggregates.match(Filters.and(filters));
    }

    public static String arrayElement(final int index) {
        return ITEM + index;
    }

    public static String fieldName(final List<String> fields) {
        return String.join(".", fields);
    }

    public static String fieldName(final String parentFieldName, final String fieldName) {
        return parentFieldName + "." + fieldName;
    }

    public static Bson arrayFilter(final String arrayElement, final NodeIdentifierWithPredicates identifier) {
        final List<Bson> filters = new ArrayList<>();
        identifier.getKeyValues().forEach((k, v) -> {
            filters.add(Filters.eq(fieldName(arrayElement, k.getLocalName()), v));
        });

        return Filters.and(filters);
    }

    public static Document arrayItemKey(final NodeIdentifierWithPredicates identifier) {
        final Document doc = new Document();
        identifier.getKeyValues().forEach((k, v) -> {
            doc.append(k.getLocalName(), v);
        });

        return doc;
    }

    @Override
    public void close() {
        //
    }

    public DOMSchemaService getDomSchemaService() {
        return domSchemaService;
    }

    public String getCollectionName(final YangInstanceIdentifier yii) {
        final Module module =
                getSchemaContext().findModule(yii.getLastPathArgument().getNodeType().getModule()).get();
        return getCollectionName(module);
    }

    public static String getCollectionName(final Module module) {
        if (module.getRevision().isPresent()) {
            return module.getNamespace().toString() + "@" + module.getRevision().get();
        }

        return module.getNamespace().toString();
    }
}

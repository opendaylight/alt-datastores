/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.impl;

import static com.mongodb.client.model.Updates.pull;
import static com.mongodb.client.model.Updates.push;
import static com.mongodb.client.model.Updates.set;
import static com.mongodb.client.model.Updates.unset;

import com.google.common.collect.ImmutableList;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import net.jcip.annotations.ThreadSafe;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.opendaylight.datastore.yongo.spi.BsonResult;
import org.opendaylight.datastore.yongo.spi.UpdateBsonResult;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * A utility class converting yang instance identifier to BSON expression.
 *
 * @author Jie Han
 */
@ThreadSafe
public abstract class YangBson implements BiConsumer<NodeIdentifierWithPredicates, Boolean>, BsonResult {
    protected final List<String> fields = new ArrayList<>();
    protected final List<Bson> bsons = new ArrayList<>();
    private final SchemaContext schemaContext;
    private String fieldName = null;
    private String simpleFieldName = null;

    protected YangBson(final SchemaContext schemaContext) {
        this.schemaContext = schemaContext;
    }

    @Override
    public final List<Bson> getBsons() {
        return ImmutableList.copyOf(bsons);
    }

    @Override
    public final String getSimpleFieldName() {
        return simpleFieldName;
    }

    @Override
    public final String getFieldName() {
        return fieldName;
    }

    protected final String getCurSimpleFieldName() {
        return fields.get(fields.size() - 1);
    }

    protected final String getCurFieldName() {
        return YangMongo.fieldName(fields);
    }

    protected BsonResult parse(final YangInstanceIdentifier yii) {
        final Iterator<PathArgument> it = yii.getPathArguments().iterator();
        PathArgument pathArgument = it.next();
        fields.add(YangMongo.getTopLevelFieldName(schemaContext, pathArgument));

        while (it.hasNext()) {
            pathArgument = it.next();
            if (pathArgument instanceof NodeIdentifierWithPredicates) {
                this.accept((NodeIdentifierWithPredicates) pathArgument, !it.hasNext());
            } else if (pathArgument instanceof YangInstanceIdentifier.NodeIdentifier) {
                fields.add(pathArgument.getNodeType().getLocalName());
            } else {
                throw new UnsupportedOperationException(
                        String.format("Unsupported pathArgument %s", pathArgument.getClass()));
            }
        }

        this.fieldName = getCurFieldName();
        this.simpleFieldName = getCurSimpleFieldName();

        return this;
    }

    private abstract static class BsonPipeline extends YangBson {
        BsonPipeline(final SchemaContext schemaContext) {
            super(schemaContext);
        }

        @Override
        public void accept(final NodeIdentifierWithPredicates pathArgument, final Boolean isLast) {
            String curFieldName = getCurFieldName();
            bsons.add(YangMongo.unwindState(curFieldName));
            bsons.add(YangMongo.matchState(curFieldName, pathArgument));
        }
    }

    private static class BsonPipelineRead extends BsonPipeline {
        BsonPipelineRead(final SchemaContext schemaContext) {
            super(schemaContext);
        }

        @Override
        public BsonResult parse(final YangInstanceIdentifier yii) {
            super.parse(yii);
            bsons.add(Aggregates.project(Projections.fields(
                    Projections.excludeId(),
                    Projections.computed(getSimpleFieldName(), "$" + getFieldName()))));

            return this;
        }
    }

    private abstract static class BsonUpdate extends YangBson implements UpdateBsonResult {
        private int index = 0;
        protected  Bson update;

        BsonUpdate(final SchemaContext schemaContext) {
            super(schemaContext);
        }

        @Override
        public void accept(final NodeIdentifierWithPredicates pathArgument, final Boolean isLast) {
            final String elem = YangMongo.arrayElement(index);
            if (!isLast) {
                fields.add("$[" + elem + "]");
                bsons.add(YangMongo.arrayFilter(elem, pathArgument));
            }
            index++;
        }

        @Override
        public Bson getUpdate() {
            return update;
        }
    }

    private static final class BsonUpdateAdd extends BsonUpdate {
        final Document updateDoc;

        BsonUpdateAdd(final SchemaContext schemaContext, final Document updateDoc) {
            super(schemaContext);
            this.updateDoc = updateDoc;
        }

        @Override
        public UpdateBsonResult parse(final YangInstanceIdentifier yii) {
            super.parse(yii);
            if (yii.getLastPathArgument() instanceof NodeIdentifierWithPredicates) {
                update = push(getFieldName(), updateDoc);
            } else if (yii.getLastPathArgument()  instanceof NodeIdentifier) {
                update = set(getFieldName(), updateDoc);
            } else {
                throw new UnsupportedOperationException(
                        String.format("Unsupported pathArgument %s", yii.getLastPathArgument().getClass()));
            }
            return this;
        }
    }

    private static final class BsonUpdateDel extends BsonUpdate {

        BsonUpdateDel(final SchemaContext schemaContext) {
            super(schemaContext);
        }

        @Override
        public UpdateBsonResult parse(final YangInstanceIdentifier yii) {
            super.parse(yii);
            if (yii.getLastPathArgument() instanceof NodeIdentifierWithPredicates) {
                update = pull(getFieldName(),
                        YangMongo.arrayItemKey((NodeIdentifierWithPredicates) yii.getLastPathArgument()));
            } else if (yii.getLastPathArgument() instanceof NodeIdentifier) {
                update = unset(getFieldName());
            } else {
                throw new UnsupportedOperationException(
                        String.format("Unsupported pathArgument %s", yii.getLastPathArgument().getClass()));
            }

            return this;
        }
    }

    public static UpdateBsonResult parseUpdateAdd(final SchemaContext schemaContext, final YangInstanceIdentifier yii,
                                                  final Document updateDoc) {
        return new BsonUpdateAdd(schemaContext, updateDoc).parse(yii);
    }

    public static UpdateBsonResult parseUpdateDel(final SchemaContext schemaContext,
                                                  final YangInstanceIdentifier yii) {
        return new BsonUpdateDel(schemaContext).parse(yii);
    }

    public static BsonResult parseStageRead(final SchemaContext schemaContext, final YangInstanceIdentifier yii) {
        return new BsonPipelineRead(schemaContext).parse(yii);
    }
}

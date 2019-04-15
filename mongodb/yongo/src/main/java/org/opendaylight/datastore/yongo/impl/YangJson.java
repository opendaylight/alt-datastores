/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.impl;

import static java.util.Objects.requireNonNull;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.jcip.annotations.ThreadSafe;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class converting yang data to JSON stream.
 *
 * @author Jie Han
 */
@ThreadSafe
public class YangJson {
    private static final Logger LOG = LoggerFactory.getLogger(YangJson.class);

    private final DOMSchemaService domSchemaService;

    public YangJson(final DOMSchemaService domSchemaService) {
        this.domSchemaService = domSchemaService;
    }

    public String normalizedNodeToJsonStream(final YangInstanceIdentifier yii,
            final NormalizedNode<?, ?> inputStructure) {
        final SchemaPath schemaPath = getSchemaPath(yii);
        try {
            if (schemaPath.getParent().equals(SchemaPath.ROOT)) {
                return topNormalizedNodeToJsonStream(inputStructure);
            }
            return nestedNormalizedNodeToJsonStream(schemaPath, inputStructure);
        } catch (IOException ex) {
            LOG.error("failed.", ex);
            return null;
        }
    }

    private String nestedNormalizedNodeToJsonStream(final SchemaPath path,
            final NormalizedNode<?, ?> inputStructure) throws IOException {
        final Writer writer = new StringWriter();
        final NormalizedNodeStreamWriter jsonStream = JSONNormalizedNodeStreamWriter.createNestedWriter(
            JSONCodecFactorySupplier.DRAFT_LHOTKA_NETMOD_YANG_JSON_02.getShared(domSchemaService.getGlobalContext()),
                path, null, JsonWriterFactory.createJsonWriter(writer, 2));
        final NormalizedNodeWriter nodeWriter = NormalizedNodeWriter.forStreamWriter(jsonStream);
        nodeWriter.write(inputStructure);

        nodeWriter.close();
        return writer.toString();
    }

    private String topNormalizedNodeToJsonStream(final NormalizedNode<?, ?> inputStructure) throws IOException {
        final Writer writer = new StringWriter();
        final NormalizedNodeStreamWriter jsonStream = JSONNormalizedNodeStreamWriter.createExclusiveWriter(
            JSONCodecFactorySupplier.DRAFT_LHOTKA_NETMOD_YANG_JSON_02.getShared(domSchemaService.getGlobalContext()),
                SchemaPath.ROOT, null, JsonWriterFactory.createJsonWriter(writer, 2));
        final NormalizedNodeWriter nodeWriter = NormalizedNodeWriter.forStreamWriter(jsonStream);
        nodeWriter.write(inputStructure);

        nodeWriter.close();
        return writer.toString();
    }

    public NormalizedNode<?, ?> jsonStreamToNormalizedNode(final YangInstanceIdentifier yii,
            final String json) {
        final SchemaPath schemaPath = getSchemaPath(yii);

        return jsonStreamToNormalizedNode(schemaPath, json);
    }

    public NormalizedNode<?, ?> jsonStreamToNormalizedNode(final SchemaPath schemaPath,
                                                           final String json) {
        final NormalizedNodeResult result = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter streamWriter = ImmutableNormalizedNodeStreamWriter.from(result);
        final JsonParserStream jsonParser;
        if (schemaPath.getParent().equals(SchemaPath.ROOT)) {
            jsonParser = JsonParserStream.create(streamWriter, JSONCodecFactorySupplier.DRAFT_LHOTKA_NETMOD_YANG_JSON_02
                    .getShared(domSchemaService.getGlobalContext()));
        } else {
            SchemaNode parent = SchemaContextUtil.findDataSchemaNode(domSchemaService.getGlobalContext(),
                    schemaPath.getParent());
            jsonParser = JsonParserStream.create(streamWriter, JSONCodecFactorySupplier.DRAFT_LHOTKA_NETMOD_YANG_JSON_02
                    .getShared(domSchemaService.getGlobalContext()), parent);
        }

        jsonParser.parse(new JsonReader(new StringReader(json)));
        final NormalizedNode<?, ?> transformedInput = result.getResult();
        NormalizedNode<?, ?> node = transformedInput;
        if (node instanceof MapNode) {
            node = ((MapNode)transformedInput).getValue().iterator().next();
        }
        return node;
    }

    private static SchemaPath getSchemaPath(final YangInstanceIdentifier yii) {
        final List<QName> qnames = new ArrayList<>();

        final List<YangInstanceIdentifier.PathArgument> pathArguments = new ArrayList<>();
        yii.getPathArguments().forEach(p -> {
            if (!(p instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates)) {
                pathArguments.add(p);
            }
        });

        Iterator<YangInstanceIdentifier.PathArgument> iterator = pathArguments.iterator();
        while (iterator.hasNext()) {
            qnames.add(iterator.next().getNodeType());
        }

        return SchemaPath.create(qnames, true);
    }

    public static boolean isTopLevel(final YangInstanceIdentifier yii) {
        final Iterator it = yii.getPathArguments().iterator();
        requireNonNull(it.next());
        if (it.hasNext()) {
            return false;
        }

        return true;
    }

    public static boolean isListElement(final YangInstanceIdentifier yii) {
        return yii.getLastPathArgument() instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates;
    }

}

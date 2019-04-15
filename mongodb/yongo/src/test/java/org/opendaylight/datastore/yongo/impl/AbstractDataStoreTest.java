/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.impl;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.mongodb.client.MongoDatabase;
import javassist.ClassPool;
import org.junit.After;
import org.junit.Before;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.dom.adapter.BindingAdapterFactory;
import org.opendaylight.mdsal.binding.dom.adapter.BindingToNormalizedNodeCodec;
import org.opendaylight.mdsal.binding.dom.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.mdsal.binding.dom.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.mdsal.binding.generator.impl.GeneratedClassLoadingStrategy;
import org.opendaylight.mdsal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.mdsal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.generator.util.JavassistUtils;
import org.opendaylight.mdsal.binding.spec.reflect.BindingReflections;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public abstract class AbstractDataStoreTest {
    private final DataBroker dataBroker;
    private final YongoDOMDataBroker domDataBroker;

    @SuppressWarnings("checkstyle:IllegalCatch")
    public AbstractDataStoreTest() {
        final JavassistUtils utils = JavassistUtils.forClassPool(ClassPool.getDefault());
        ModuleInfoBackedContext ctx = ModuleInfoBackedContext.create();
        ctx.addModuleInfos(BindingReflections.loadModuleInfos());
        final SchemaContext schemaContext = ctx.tryToCreateSchemaContext().get();
        final BindingRuntimeContext runtimeContext = BindingRuntimeContext.create(ctx, schemaContext);
        final BindingNormalizedNodeCodecRegistry registry =
                new BindingNormalizedNodeCodecRegistry(StreamWriterGenerator.create(utils));
        registry.onBindingRuntimeContextUpdated(runtimeContext);

        final GeneratedClassLoadingStrategy loading = GeneratedClassLoadingStrategy.getTCCLClassLoadingStrategy();

        final DOMSchemaService schemaService = mock(DOMSchemaService.class);
        doReturn(schemaContext).when(schemaService).getGlobalContext();
        domDataBroker = new YongoDOMDataBroker(schemaService, MongoContainer.INSTANCE.getMongoClient(),
                MongoContainer.INSTANCE.getRSMongoClient());
        dataBroker = new YongoBindingDataBroker(
                new BindingAdapterFactory(new BindingToNormalizedNodeCodec(loading, registry)),
                domDataBroker);
    }

    @After
    public void tearDown() throws Exception {
        MongoDatabase confDB = domDataBroker.getDataStore().getYongoService()
                .getDatabase(LogicalDatastoreType.CONFIGURATION);
        confDB.drop();

        MongoDatabase operDB = domDataBroker.getDataStore().getYongoService()
                .getDatabase(LogicalDatastoreType.OPERATIONAL);
        operDB.drop();
        domDataBroker.close();
    }

    @Before
    public void setUp() {

    }

    public DataBroker getDataBroker() {
        return dataBroker;
    }

    public DOMDataBroker getDomDataBroker() {
        return domDataBroker;
    }
}
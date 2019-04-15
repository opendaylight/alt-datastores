/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.impl;

import com.mongodb.client.MongoClient;
import java.util.concurrent.atomic.AtomicLong;
import net.jcip.annotations.ThreadSafe;
import org.opendaylight.datastore.yongo.spi.YongoService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * MongoDB based date store, which provides CRUD operations by adapting MongoDB java driver.
 *
 * @author Jie Han
 */
@ThreadSafe
public class YongoDataStore implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(YongoDataStore.class);
    public static final String ID_PREFIX = "yongo-tx";
    private final YangJson yangJson;
    private final YongoService yongoService;
    private final AtomicLong txCounter = new AtomicLong(0);

    public YongoDataStore(final DOMSchemaService schemaService, final MongoClient mongoClient) {
        this.yangJson = new YangJson(schemaService);
        this.yongoService = YangMongo.newInstance(mongoClient, yangJson, schemaService);
    }

    public YongoService getYongoService() {
        return yongoService;
    }

    public String nextIdentifier() {
        return ID_PREFIX + "-" + txCounter.getAndIncrement();
    }

    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        LOG.info("YongoDataStore Session Initiated");
    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() throws Exception {
        LOG.info("YongoDataStore Closed");
        yongoService.close();
    }

    public YangJson getYangJson() {
        return yangJson;
    }
}
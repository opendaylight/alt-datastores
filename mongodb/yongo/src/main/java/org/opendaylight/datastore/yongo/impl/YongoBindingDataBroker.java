/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.impl;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.dom.adapter.spi.AdapterFactory;
import org.opendaylight.mdsal.binding.spi.ForwardingDataBroker;

/**
 * {@link DataBroker} registered in the OSGi service registry.
 *
 * @author Jie Han
 */
@Singleton
public class YongoBindingDataBroker extends ForwardingDataBroker {
    private final DataBroker dataBroker;

    @Inject
    public YongoBindingDataBroker(@Reference AdapterFactory adapterFactory, YongoDOMDataBroker domDataBroker) {
        this.dataBroker = adapterFactory.createDataBroker(domDataBroker);
    }

    @Override
    protected @NonNull DataBroker delegate() {
        return this.dataBroker;
    }
}
/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.spi;

import java.util.List;
import org.bson.conversions.Bson;
import org.opendaylight.datastore.yongo.impl.YangBson;


/**
 * An interface which represents the parsed result of {@link YangBson}.
 *
 * @author Jie Han
 */
public interface BsonResult {
    List<Bson> getBsons();

    String getSimpleFieldName();

    String getFieldName();
}

/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.yangtools.copypaste;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidateNode;
import org.opendaylight.yangtools.yang.data.api.schema.tree.ModificationType;

/**
 * Utility implementation of {@link DataTreeCandidateNode} which acts as if
 * the {@link NormalizedNode} passed to it at creation time were freshly written.
 */
public final class NormalizedNodeDataTreeCandidateNode implements DataTreeCandidateNode {
    private final NormalizedNode<?, ?> data;
    private final ModificationType type;

    /**
     * Create a new instance backed by supplied data.
     *
     * @param data Backing {@link NormalizedNode} data.
     */
    public  NormalizedNodeDataTreeCandidateNode(@Nonnull final NormalizedNode<?, ?> data, final ModificationType type) {
        this.data = requireNonNull(data);
        this.type = requireNonNull(type);
    }

    @Nonnull
    @Override
    public PathArgument getIdentifier() {
        return data.getIdentifier();
    }

    @Nonnull
    @Override
    public Collection<DataTreeCandidateNode> getChildNodes() {
        if (data instanceof NormalizedNodeContainer) {
            return Collections2.transform(((NormalizedNodeContainer<?, ?, ?>) data).getValue(),
                input -> input == null ? null : new NormalizedNodeDataTreeCandidateNode(input, type));
        }
        return ImmutableList.of();
    }

    @Override
    public DataTreeCandidateNode getModifiedChild(final PathArgument childIdentifier) {
        if (data instanceof NormalizedNodeContainer) {
            @SuppressWarnings({ "rawtypes", "unchecked" })
            final Optional<? extends NormalizedNode<?, ?>> child =
                ((NormalizedNodeContainer)data).getChild(childIdentifier);
            return child.map(input -> new NormalizedNodeDataTreeCandidateNode(input, type)).orElse(null);
        }
        return null;
    }

    @Nonnull
    @Override
    public ModificationType getModificationType() {
        return type;
    }

    @Nonnull
    @Override
    public Optional<NormalizedNode<?, ?>> getDataAfter() {
        return Optional.of(data);
    }

    @Nonnull
    @Override
    public Optional<NormalizedNode<?, ?>> getDataBefore() {
        return Optional.empty();
    }
}

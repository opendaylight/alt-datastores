/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.datastore.yongo.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.binding.api.ReadWriteTransaction;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.ChoiceContainer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.Top;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.TopBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.two.level.list.TopLevelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.two.level.list.TopLevelListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.two.level.list.TopLevelListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.two.level.list.top.level.list.NestedList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.two.level.list.top.level.list.NestedListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.two.level.list.top.level.list.NestedListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsal.test.binding.rev140701.two.level.list.top.level.list.choice.in.list.SimpleCaseBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class YongoDataStoreTest extends AbstractDataStoreTest {

    private static final List<TopLevelList> TWO_LIST = createList(2, 2);
    private static final List<TopLevelList> THREE_LIST = createList(3, 3);
    private static final List<NestedList> TWO_NESTED_LIST = createNestedList(2);
    private static final List<NestedList> THREE_NESTED_LIST = createNestedList(3);
    private static final Top TOP_TWO_LIST_DATA = new TopBuilder().setTopLevelList(TWO_LIST).build();
    private static final Top TOP_THREE_LIST_DATA = new TopBuilder().setTopLevelList(THREE_LIST).build();
    private static final Top TOP_THIRD_LIST_DATA =
            new TopBuilder().setTopLevelList(Arrays.asList(THREE_LIST.get(2))).build();

    private static final String TOP_LEVEL_LIST_FOO_KEY_VALUE = "test-0";
    private static final String NESTED_LIST_FOO_KEY_VALUE = "nest-test-0";
    private static final String NESTED_LIST_2_KEY_VALUE = "nest-test-2";
    private static final TopLevelListKey TOP_LEVEL_LIST_FOO_KEY = new TopLevelListKey(TOP_LEVEL_LIST_FOO_KEY_VALUE);
    private static final NestedListKey NESTED_LIST_FOO_KEY = new NestedListKey(NESTED_LIST_FOO_KEY_VALUE);
    private static final NestedListKey NESTED_LIST_2_KEY = new NestedListKey(NESTED_LIST_2_KEY_VALUE);

    private static final QName TOP_QNAME = Top.QNAME;
    private static final QName TOP_LEVEL_LIST_QNAME = QName.create(TOP_QNAME, "top-level-list");
    private static final QName TOP_LEVEL_LIST_KEY_QNAME = QName.create(TOP_QNAME, "name");
    private static final QName TOP_LEVEL_LEAF_LIST_QNAME = QName.create(TOP_QNAME, "top-level-leaf-list");
    private static final QName TOP_LEVEL_ORDERED_LEAF_LIST_QNAME = QName.create(TOP_QNAME,
            "top-level-ordered-leaf-list");
    private static final QName NESTED_LIST_QNAME = QName.create(TOP_QNAME, "nested-list");
    private static final QName NESTED_LIST_KEY_QNAME = QName.create(TOP_QNAME, "name");
    private static final QName CHOICE_CONTAINER_QNAME = ChoiceContainer.QNAME;
    private static final QName CHOICE_IDENTIFIER_QNAME = QName.create(CHOICE_CONTAINER_QNAME, "identifier");
    private static final QName CHOICE_IDENTIFIER_ID_QNAME = QName.create(CHOICE_CONTAINER_QNAME, "id");
    private static final QName SIMPLE_ID_QNAME = QName.create(CHOICE_CONTAINER_QNAME, "simple-id");
    private static final QName EXTENDED_ID_QNAME = QName.create(CHOICE_CONTAINER_QNAME, "extended-id");
    private static final YangInstanceIdentifier BI_CHOICE_CONTAINER_PATH = YangInstanceIdentifier.of(
            CHOICE_CONTAINER_QNAME);

    private static final InstanceIdentifier<Top> BA_TOP = InstanceIdentifier
            .builder(Top.class).build();

    private static final InstanceIdentifier<TopLevelList> BA_TOP_LEVEL_LIST = InstanceIdentifier
            .builder(Top.class).child(TopLevelList.class, TOP_LEVEL_LIST_FOO_KEY).build();

    private static final InstanceIdentifier<NestedList> BA_NESTED_LIST = InstanceIdentifier
            .builder(Top.class).child(TopLevelList.class, TOP_LEVEL_LIST_FOO_KEY)
            .child(NestedList.class, NESTED_LIST_FOO_KEY).build();

    private static final InstanceIdentifier<NestedList> BA_NESTED_LIST_2 = InstanceIdentifier
            .builder(Top.class).child(TopLevelList.class, TOP_LEVEL_LIST_FOO_KEY)
            .child(NestedList.class, NESTED_LIST_2_KEY).build();

    private static final YangInstanceIdentifier BI_TOP_PATH = YangInstanceIdentifier.of(TOP_QNAME);
    private static final YangInstanceIdentifier BI_TOP_LEVEL_LIST_PATH = BI_TOP_PATH.node(TOP_LEVEL_LIST_QNAME);
    private static final YangInstanceIdentifier BI_TOP_LEVEL_LIST_FOO_PATH = BI_TOP_LEVEL_LIST_PATH
            .node(new YangInstanceIdentifier.NodeIdentifierWithPredicates(TOP_LEVEL_LIST_QNAME,
                    TOP_LEVEL_LIST_KEY_QNAME, TOP_LEVEL_LIST_FOO_KEY_VALUE));

    @Test
    public void testPutTop() throws ExecutionException, InterruptedException {
        ReadWriteTransaction tx = getDataBroker().newReadWriteTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, BA_TOP, TOP_TWO_LIST_DATA);
        tx.commit().get();

        ReadTransaction tx2 = getDataBroker().newReadOnlyTransaction();
        Optional<Top> top2 = tx2.read(LogicalDatastoreType.CONFIGURATION, BA_TOP).get();
        tx2.close();
        assertEquals(TOP_TWO_LIST_DATA, top2.get());
    }

    @Test
    public void testDeleteTop() throws ExecutionException, InterruptedException {
        ReadWriteTransaction tx = getDataBroker().newReadWriteTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, BA_TOP, TOP_TWO_LIST_DATA);
        tx.commit().get();

        ReadTransaction tx2 = getDataBroker().newReadOnlyTransaction();
        Optional<Top> top2 = tx2.read(LogicalDatastoreType.CONFIGURATION, BA_TOP).get();
        tx2.close();
        assertEquals(TOP_TWO_LIST_DATA, top2.get());

        ReadWriteTransaction tx3 = getDataBroker().newReadWriteTransaction();
        tx3.delete(LogicalDatastoreType.CONFIGURATION, BA_TOP);
        tx3.commit().get();

        ReadTransaction tx4 = getDataBroker().newReadOnlyTransaction();
        Optional<Top> top4 = tx4.read(LogicalDatastoreType.CONFIGURATION, BA_TOP).get();
        tx4.close();
        assertFalse(top4.isPresent());
    }

    @Test
    public void testMergeTop() throws ExecutionException, InterruptedException {
        ReadWriteTransaction tx = getDataBroker().newReadWriteTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, BA_TOP, TOP_TWO_LIST_DATA);
        tx.commit().get();

        ReadWriteTransaction tx2 = getDataBroker().newReadWriteTransaction();
        tx2.merge(LogicalDatastoreType.CONFIGURATION, BA_TOP, TOP_THREE_LIST_DATA);
        tx2.commit().get();

        ReadTransaction tx3 = getDataBroker().newReadOnlyTransaction();
        Optional<Top> top3 = tx3.read(LogicalDatastoreType.CONFIGURATION, BA_TOP).get();
        tx3.close();
        assertEquals(TOP_THREE_LIST_DATA, top3.get());
    }

    @Test
    public void testMergeTopLevelList() throws ExecutionException, InterruptedException {
        ReadWriteTransaction tx = getDataBroker().newReadWriteTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, BA_TOP, TOP_TWO_LIST_DATA);
        tx.commit().get();

        ReadWriteTransaction tx2 = getDataBroker().newReadWriteTransaction();
        tx2.merge(LogicalDatastoreType.CONFIGURATION, BA_TOP_LEVEL_LIST, THREE_LIST.get(0));
        tx2.commit().get();

        ReadTransaction tx3 = getDataBroker().newReadOnlyTransaction();
        Optional<Top> top3 = tx3.read(LogicalDatastoreType.CONFIGURATION, BA_TOP).get();
        tx3.close();
        assertEquals(THREE_LIST.get(0), top3.get().getTopLevelList().get(0));
    }

    @Test
    public void testPutNestedList() throws ExecutionException, InterruptedException {
        WriteTransaction tx = getDataBroker().newReadWriteTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, BA_TOP, TOP_TWO_LIST_DATA);
        tx.put(LogicalDatastoreType.CONFIGURATION, BA_NESTED_LIST_2, THREE_NESTED_LIST.get(2));
        tx.commit().get();

        ReadTransaction tx2 = getDataBroker().newReadOnlyTransaction();
        Optional<Top> top2 = tx2.read(LogicalDatastoreType.CONFIGURATION, BA_TOP).get();
        tx2.close();

        assertEquals(THREE_NESTED_LIST, top2.get().getTopLevelList().get(0).getNestedList());
    }

    @Test
    public void testReadNestedList() throws ExecutionException, InterruptedException {
        WriteTransaction tx = getDataBroker().newReadWriteTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, BA_TOP, TOP_TWO_LIST_DATA);
        tx.commit().get();

        ReadWriteTransaction tx2 = getDataBroker().newReadWriteTransaction();
        Optional<NestedList> nestedList = tx2.read(LogicalDatastoreType.CONFIGURATION, BA_NESTED_LIST).get();
        tx2.commit().get();
        assertEquals(TWO_NESTED_LIST.get(0), nestedList.get());
    }

    @Test
    public void testReadNonExisting() throws ExecutionException, InterruptedException {
        ReadTransaction tx = getDataBroker().newReadOnlyTransaction();
        Boolean exist = tx.exists(LogicalDatastoreType.CONFIGURATION, BA_NESTED_LIST).get();
        tx.close();
        assertFalse(exist);
    }

    @Test
    public void testCancelWrite() throws ExecutionException, InterruptedException {
        WriteTransaction tx = getDataBroker().newReadWriteTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, BA_TOP, TOP_TWO_LIST_DATA);
        tx.cancel();

        ReadTransaction tx2 = getDataBroker().newReadOnlyTransaction();
        Optional<Top> top = tx2.read(LogicalDatastoreType.CONFIGURATION, BA_TOP).get();
        tx2.close();
        assertFalse(top.isPresent());
    }

    @Test
    public void testDeleteNestedList() throws ExecutionException, InterruptedException {
        WriteTransaction tx = getDataBroker().newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, BA_TOP, TOP_THREE_LIST_DATA);
        tx.commit().get();

        ReadWriteTransaction tx2 = getDataBroker().newReadWriteTransaction();
        tx2.delete(LogicalDatastoreType.CONFIGURATION, BA_NESTED_LIST_2);
        tx2.commit().get();

        ReadWriteTransaction tx3 = getDataBroker().newReadWriteTransaction();
        Optional<Top> top = tx3.read(LogicalDatastoreType.CONFIGURATION, BA_TOP).get();
        tx3.commit().get();
        assertEquals(TWO_LIST.get(0), top.get().getTopLevelList().get(0));
    }

    @Ignore
    @Test
    public void testDataChangeStream() throws ExecutionException, InterruptedException {
        final ArgumentCaptor<Collection> captor = ArgumentCaptor.forClass(
                Collection.class);
        final DataTreeChangeListener<Top> listener = mock(DataTreeChangeListener.class);
        final DataTreeIdentifier<Top> dti = DataTreeIdentifier.create(CONFIGURATION, BA_TOP);
        getDataBroker().registerDataTreeChangeListener(dti, listener);

        testDeleteNestedList();

        verify(listener, timeout(4000)).onDataTreeChanged(captor.capture());
        final DataTreeModification<Top> modification =
                (DataTreeModification<Top>) Iterables.getOnlyElement(captor.getValue());
        assertEquals(BA_TOP, modification.getRootPath().getRootIdentifier());
    }

    private static List<TopLevelList> createList(final int num, final int nestNum) {

        final ImmutableList.Builder<TopLevelList> builder = ImmutableList.builder();
        for (int i = 0; i < num; i++) {
            final TopLevelListKey key = new TopLevelListKey("test-" + i);
            builder.add(new TopLevelListBuilder().withKey(key)
                    .setChoiceInList(new SimpleCaseBuilder().setSimple("simple-case").build())
                    .setNestedList(createNestedList(nestNum)).build());
        }
        return builder.build();
    }

    private static List<NestedList> createNestedList(final int num) {

        final ImmutableList.Builder<NestedList> builder = ImmutableList.builder();
        for (int i = 0; i < num; i++) {
            final NestedListKey key = new NestedListKey("nest-test-" + i);
            builder.add(new NestedListBuilder().withKey(key).setType("nest-type-" + i).build());
        }
        return builder.build();
    }
}
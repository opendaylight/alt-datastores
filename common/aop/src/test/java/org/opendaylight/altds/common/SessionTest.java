/*
 * Copyright (c) 2019 ZTE, Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.altds.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.opendaylight.altds.common.TestOperationUtil.e;
import static org.opendaylight.altds.common.TestOperationUtil.er;
import static org.opendaylight.altds.common.TestOperationUtil.g;
import static org.opendaylight.altds.common.TestOperationUtil.ol;
import static org.opendaylight.altds.common.TestOperationUtil.or;
import static org.opendaylight.altds.common.TestOperationUtil.s;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionTest {
    private static final Logger LOG = LoggerFactory.getLogger(SessionTest.class);

    @Before
    public void beforeTest() {
        TestOperationUtil.reset();
    }

    @Test
    public void sessionTest() throws ExecutionException, InterruptedException {
        Session session = s(1, 2, 3, 4, 5);
        List<Object> result = session.submit().get();
        LOG.info("session result : {}", result);
        assertEquals(result, Arrays.asList(1,2, 3, 4, 5));
    }

    @Test
    public void sessionGroupTest() throws ExecutionException, InterruptedException {
        Session session = s(g(1, 2, 3, 4), 5);
        List<Object> result = session.submit().get();
        LOG.info("session result : {}", result);
        assertEquals(result, Arrays.asList(4, 5));
    }

    @Test
    public void sessionInGroupTest() {
        Session session = s(g(9, g(1, 2, s(6, 7, 8), ol(3), 4), 5), 10);
        try {
            session.submit().get();
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(e.getCause() instanceof OperationException);
        }
    }

    @Test
    public void rollbackGroupExceptionTailTest() {
        Session session = s(g(1, 2, 3, e(4)), 5, 6, 7);
        try {
            session.submit().get();
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(e.getCause() instanceof TestOperationUtil.TestException);
        }
    }

    @Test
    public void rollbackGroupExceptionNormalTest() {
        Session session = s(g(1, 2, e(3), 4), 5);
        try {
            session.submit().get();
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(e.getCause() instanceof TestOperationUtil.TestException);
        }
    }

    @Test
    public void rollbackGroupExceptionHeadTest() {
        Session session = s(g(e(1), 2, 3, 4), 5);
        try {
            session.submit().get();
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(e.getCause() instanceof TestOperationUtil.TestException);
        }
    }

    @Test
    public void rollbackSessionInSessionTest() {
        Session session = s(g(1, 2, 3), 4, s(e(11), g(5, 6)));
        try {
            session.submit().get();
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(e.getCause() instanceof TestOperationUtil.TestException);
        }
    }

    @Test
    public void rollbackSessionInGroupTest() {
        Session session = s(g(5, s(g(1, 2, e(3)), 4)), 6, 7);
        try {
            session.submit().get();
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(e.getCause() instanceof TestOperationUtil.TestException);
        }
    }

    @Test
    public void rpcSessionTest() throws ExecutionException, InterruptedException {
        Session session = s(g(or(1), or(2), or(3), or(4)));
        List<Object> result = session.submit().get();
        LOG.info("session result : {}", result);
        assertEquals(result, Arrays.asList(new TestOperationUtil.TestRpcData(4)));
    }

    @Test
    public void rpcExSessionTest() {
        Session session = s(g(or(1), or(2), er(3), or(4)));
        try {
            session.submit().get();
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(e.getCause() instanceof TestOperationUtil.TestException);
        }
    }

    @Test
    public void conditionalGroupFalseTest() {
        Session session = s(1, g(2, g(3, 4).cond(t -> (Integer) t != 2)));
        List<Object> result = null;
        try {
            result = session.submit().get();
        } catch (InterruptedException | ExecutionException e) {
            fail("error");
        }

        assertEquals(Arrays.asList(1, null), result);
    }

    @Test
    public void conditionalGroupTrueTest() {
        Session session = s(1, g(2, g(3, 4).cond(t -> (Integer) t == 2)));
        List<Object> result = null;
        try {
            result = session.submit().get();
        } catch (InterruptedException | ExecutionException e) {
            fail("error");
        }

        assertEquals(Arrays.asList(1, 4), result);
    }

    @Test
    public void conditionalGroupExceptionTest() {
        Session session = s(1, g(2, g(3, 4).cond(t -> {
            throw new RuntimeException();
        })));

        try {
            session.submit().get();
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(e.getCause() instanceof RuntimeException);
        }
    }

    @Test
    public void conditionalSessionFalseTest() {
        Session session = s(1, g(2, s(3, 4).cond(t -> (Integer) t != 2)));
        List<Object> result = null;
        try {
            result = session.submit().get();
        } catch (InterruptedException | ExecutionException e) {
            fail("error");
        }

        assertEquals(Arrays.asList(1, ImmutableList.of()), result);
    }

    @Test
    public void conditionalSessionTrueTest() {
        Session session = s(1, g(2, s(3, 4).cond(t -> (Integer) t == 2)));
        List<Object> result = null;
        try {
            result = session.submit().get();
        } catch (InterruptedException | ExecutionException e) {
            fail("error");
        }

        assertEquals(Arrays.asList(1, Arrays.asList(3, 4)), result);
    }

    @Test
    public void conditionalSessionExceptionTest() {
        Session session = s(1, g(2, s(3, 4).cond(t -> {
            throw new RuntimeException();
        })));

        try {
            session.submit().get();
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(e.getCause() instanceof RuntimeException);
        }
    }
}
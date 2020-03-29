/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.PrimitiveId;
import io.atomix.primitive.session.SessionId;
import io.atomix.raft.ReadConsistency;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.impl.RaftServiceManager;
import io.atomix.raft.protocol.RaftServerProtocol;
import io.atomix.raft.service.RaftServiceContext;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.concurrent.ThreadContextFactory;
import io.atomix.utils.serializer.Namespaces;
import io.atomix.utils.serializer.Serializer;
import org.junit.Test;

/** Raft session manager test. */
public class RaftSessionRegistryTest {

  @Test
  public void testAddRemoveSession() throws Exception {
    final RaftSessionRegistry sessionManager = new RaftSessionRegistry();
    final RaftSession session = createSession(1);
    sessionManager.addSession(session);
    assertNotNull(sessionManager.getSession(1));
    assertNotNull(sessionManager.getSession(session.sessionId()));
    assertEquals(0, sessionManager.getSessions(PrimitiveId.from(1)).size());
    session.open();
    assertEquals(1, sessionManager.getSessions(PrimitiveId.from(1)).size());
    sessionManager.removeSession(SessionId.from(1));
    assertNull(sessionManager.getSession(1));
  }

  private RaftSession createSession(final long sessionId) {
    final RaftServiceContext context = mock(RaftServiceContext.class);
    when(context.serviceType()).thenReturn(TestPrimitiveType.instance());
    when(context.serviceName()).thenReturn("test");
    when(context.serviceId()).thenReturn(PrimitiveId.from(1));

    final RaftContext server = mock(RaftContext.class);
    when(server.getProtocol()).thenReturn(mock(RaftServerProtocol.class));
    final RaftServiceManager manager = mock(RaftServiceManager.class);
    when(manager.executor()).thenReturn(mock(ThreadContext.class));
    when(server.getServiceManager()).thenReturn(manager);

    return new RaftSession(
        SessionId.from(sessionId),
        MemberId.from("1"),
        "test",
        TestPrimitiveType.instance(),
        ReadConsistency.LINEARIZABLE,
        100,
        5000,
        System.currentTimeMillis(),
        Serializer.using(Namespaces.BASIC),
        context,
        server,
        mock(ThreadContextFactory.class));
  }
}

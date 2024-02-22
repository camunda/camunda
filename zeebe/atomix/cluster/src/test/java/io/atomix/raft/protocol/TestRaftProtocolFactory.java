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
package io.atomix.raft.protocol;

import static org.mockito.Mockito.spy;

import com.google.common.collect.Maps;
import io.atomix.cluster.MemberId;
import io.atomix.utils.concurrent.ThreadContext;
import java.util.Map;

/** Test Raft protocol factory. */
public class TestRaftProtocolFactory {

  private final Map<MemberId, TestRaftServerProtocol> servers = Maps.newConcurrentMap();
  private final ThreadContext context;

  public TestRaftProtocolFactory(final ThreadContext context) {
    this.context = context;
  }

  /**
   * Returns a new test server protocol.
   *
   * @param memberId the server member identifier
   * @return a new test server protocol
   */
  public TestRaftServerProtocol newServerProtocol(final MemberId memberId) {
    final TestRaftServerProtocol spyProtocol =
        spy(new TestRaftServerProtocol(memberId, servers, context));
    servers.put(memberId, spyProtocol);
    return spyProtocol;
  }

  /** Disconnect server from rest of the servers */
  public void partition(final MemberId target) {
    servers.keySet().forEach(other -> partition(target, other));
  }

  /** Disconnect two members */
  private void partition(final MemberId first, final MemberId second) {
    servers.get(first).disconnect(second);
    servers.get(second).disconnect(first);
  }

  /** Heal network partition between target and rest of the cluster */
  public void heal(final MemberId target) {
    servers.keySet().forEach(other -> heal(target, other));
  }

  /** Heal network partition between two members */
  private void heal(final MemberId first, final MemberId second) {
    servers.get(first).reconnect(second);
    servers.get(second).reconnect(first);
  }
}

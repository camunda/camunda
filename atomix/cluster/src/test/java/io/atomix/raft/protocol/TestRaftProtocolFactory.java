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

import com.google.common.collect.Maps;
import io.atomix.cluster.MemberId;
import io.atomix.utils.concurrent.ThreadContext;
import java.util.Map;

/** Test Raft protocol factory. */
public class TestRaftProtocolFactory {

  private final Map<MemberId, TestRaftServerProtocol> servers = Maps.newConcurrentMap();
  private final Map<MemberId, TestRaftClientProtocol> clients = Maps.newConcurrentMap();
  private final ThreadContext context;

  public TestRaftProtocolFactory(final ThreadContext context) {
    this.context = context;
  }

  /**
   * Returns a new test client protocol.
   *
   * @param memberId the client member identifier
   * @return a new test client protocol
   */
  public RaftClientProtocol newClientProtocol(final MemberId memberId) {
    return new TestRaftClientProtocol(memberId, servers, clients, context);
  }

  /**
   * Returns a new test server protocol.
   *
   * @param memberId the server member identifier
   * @return a new test server protocol
   */
  public RaftServerProtocol newServerProtocol(final MemberId memberId) {
    return new TestRaftServerProtocol(memberId, servers, clients, context);
  }
}

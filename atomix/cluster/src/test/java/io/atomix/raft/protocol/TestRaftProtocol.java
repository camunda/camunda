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

import io.atomix.cluster.MemberId;
import io.atomix.utils.concurrent.Scheduled;
import io.atomix.utils.concurrent.ThreadContext;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/** Base class for Raft protocol. */
public abstract class TestRaftProtocol {

  private final Map<MemberId, TestRaftServerProtocol> servers;
  private final Map<MemberId, TestRaftClientProtocol> clients;
  private final ThreadContext context;

  public TestRaftProtocol(
      final Map<MemberId, TestRaftServerProtocol> servers,
      final Map<MemberId, TestRaftClientProtocol> clients,
      final ThreadContext context) {
    this.servers = servers;
    this.clients = clients;
    this.context = context;
  }

  <T> CompletableFuture<T> scheduleTimeout(final CompletableFuture<T> future) {
    final Scheduled scheduled =
        context.schedule(
            Duration.ofSeconds(1),
            () -> {
              if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException());
              }
            });
    return future.whenComplete((r, e) -> scheduled.cancel());
  }

  TestRaftServerProtocol server(final MemberId memberId) {
    return servers.get(memberId);
  }

  Collection<TestRaftServerProtocol> servers() {
    return servers.values();
  }

  TestRaftClientProtocol client(final MemberId memberId) {
    return clients.get(memberId);
  }
}

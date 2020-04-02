/*
 * Copyright 2016-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.partition.impl;

import static org.slf4j.LoggerFactory.getLogger;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.partition.PartitionClient;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.raft.RaftClient;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.protocol.RaftClientProtocol;
import io.atomix.raft.session.RaftSessionClient;
import io.atomix.utils.Managed;
import io.atomix.utils.concurrent.ThreadContextFactory;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

/** StoragePartition client. */
public class RaftPartitionClient implements PartitionClient, Managed<RaftPartitionClient> {

  private final Logger log = getLogger(getClass());

  private final RaftPartition partition;
  private final MemberId localMemberId;
  private final RaftClientProtocol protocol;
  private final ThreadContextFactory threadContextFactory;
  private RaftClient client;

  public RaftPartitionClient(
      final RaftPartition partition,
      final MemberId localMemberId,
      final RaftClientProtocol protocol,
      final ThreadContextFactory threadContextFactory) {
    this.partition = partition;
    this.localMemberId = localMemberId;
    this.protocol = protocol;
    this.threadContextFactory = threadContextFactory;
  }

  /**
   * Returns the partition term.
   *
   * @return the partition term
   */
  public long term() {
    return client != null ? client.term() : 0;
  }

  /**
   * Returns the partition leader.
   *
   * @return the partition leader
   */
  public MemberId leader() {
    return client != null ? client.leader() : null;
  }

  @Override
  public RaftSessionClient.Builder sessionBuilder(
      final String primitiveName,
      final PrimitiveType primitiveType,
      final ServiceConfig serviceConfig) {
    return client.sessionBuilder(primitiveName, primitiveType, serviceConfig);
  }

  @Override
  public CompletableFuture<RaftPartitionClient> start() {
    synchronized (RaftPartitionClient.this) {
      client = newRaftClient(protocol);
    }
    return client
        .connect(partition.members())
        .whenComplete(
            (r, e) -> {
              if (e == null) {
                log.debug("Successfully started client for partition {}", partition.id());
              } else {
                log.warn("Failed to start client for partition {}", partition.id(), e);
              }
            })
        .thenApply(v -> null);
  }

  @Override
  public boolean isRunning() {
    return client != null;
  }

  @Override
  public CompletableFuture<Void> stop() {
    return client != null ? client.close() : CompletableFuture.completedFuture(null);
  }

  private RaftClient newRaftClient(final RaftClientProtocol protocol) {
    return RaftClient.builder()
        .withClientId(partition.name())
        .withPartitionId(partition.id())
        .withMemberId(localMemberId)
        .withProtocol(protocol)
        .withThreadContextFactory(threadContextFactory)
        .build();
  }
}

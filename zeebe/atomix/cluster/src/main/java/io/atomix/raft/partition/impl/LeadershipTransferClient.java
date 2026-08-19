/*
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
package io.atomix.raft.partition.impl;

import static io.atomix.raft.partition.RaftPartition.PARTITION_NAME_FORMAT;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.raft.LeadershipTransferProtocol;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferInitiateResponse;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.LeadershipTransferResultResponse;
import io.atomix.utils.serializer.Serializer;
import io.camunda.cluster.PartitionId;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * Allows the rebalancing coordinator to address the leadership transfer protocol from (potentially)
 * outside the Raft group of the partition.
 */
@NullMarked
public final class LeadershipTransferClient implements LeadershipTransferProtocol, AutoCloseable {

  private final ClusterCommunicationService communicationService;
  private final Duration requestTimeout;
  private final Serializer serializer = Serializer.using(RaftNamespaces.RAFT_PROTOCOL);
  private final Set<String> subscriptions = new CopyOnWriteArraySet<>();

  public LeadershipTransferClient(
      final ClusterCommunicationService communicationService, final Duration requestTimeout) {
    this.communicationService = communicationService;
    this.requestTimeout = requestTimeout;
  }

  @Override
  public CompletableFuture<LeadershipTransferInitiateResponse> initiate(
      final MemberId leader,
      final PartitionId partitionId,
      final LeadershipTransferInitiateRequest request) {
    return communicationService.send(
        subjects(partitionId).getLeadershipTransferInitiateSubject(),
        request,
        serializer::encode,
        serializer::decode,
        leader,
        requestTimeout);
  }

  @Override
  public void onResult(
      final PartitionId partitionId,
      final Function<
              LeadershipTransferResultRequest, CompletableFuture<LeadershipTransferResultResponse>>
          handler) {
    final var subject = subjects(partitionId).getLeadershipTransferResultSubject();
    subscriptions.add(subject);
    communicationService.replyTo(subject, serializer::decode, handler, serializer::encode);
  }

  @Override
  public void close() {
    subscriptions.forEach(communicationService::unsubscribe);
    subscriptions.clear();
  }

  private RaftMessageContext subjects(final PartitionId partitionId) {
    return new RaftMessageContext(
        PARTITION_NAME_FORMAT.formatted(partitionId.group(), partitionId.number()));
  }
}

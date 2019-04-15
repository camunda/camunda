/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.distributedlog.impl;

import io.atomix.core.Atomix;
import io.atomix.protocols.raft.MultiRaftProtocol;
import io.zeebe.distributedlog.DistributedLogstream;
import io.zeebe.distributedlog.DistributedLogstreamBuilder;
import io.zeebe.distributedlog.DistributedLogstreamType;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedLogstreamPartition implements Service<DistributedLogstreamPartition> {
  private static final Logger LOG = LoggerFactory.getLogger(DistributedLogstreamPartition.class);

  private DistributedLogstream distributedLog;

  private final int partitionId;
  private final String partitionName;
  private final String primitiveName;
  private Atomix atomix;
  private String memberId;
  private final long currentLeaderTerm;
  private final Injector<Atomix> atomixInjector = new Injector<>();

  private static final MultiRaftProtocol PROTOCOL =
      MultiRaftProtocol.builder()
          // Maps partitionName to partitionId
          .withPartitioner(DistributedLogstreamName.getInstance())
          .build();

  public DistributedLogstreamPartition(int partitionId, long leaderTerm) {
    this.partitionId = partitionId;
    this.currentLeaderTerm = leaderTerm;
    primitiveName = "distributed-log"; // Use same primitive for all partitions.
    partitionName = DistributedLogstreamName.getPartitionKey(partitionId);
  }

  public long append(byte[] blockBuffer, long commitPosition) {
    return distributedLog.append(partitionName, memberId, commitPosition, blockBuffer);
  }

  public CompletableFuture<Long> asyncAppend(byte[] blockBuffer, long commitPosition) {
    return distributedLog.async().append(partitionName, memberId, commitPosition, blockBuffer);
  }

  public CompletableFuture<Boolean> claimLeaderShip() {
    return distributedLog.async().claimLeaderShip(partitionName, memberId, currentLeaderTerm);
  }

  @Override
  public void start(ServiceStartContext startContext) {
    this.atomix = atomixInjector.getValue();
    this.memberId = atomix.getMembershipService().getLocalMember().id().id();

    final CompletableFuture<DistributedLogstream> distributedLogstreamCompletableFuture =
        atomix
            .<DistributedLogstreamBuilder, DistributedLogstreamConfig, DistributedLogstream>
                primitiveBuilder(primitiveName, DistributedLogstreamType.instance())
            .withProtocol(PROTOCOL)
            .buildAsync();

    final CompletableActorFuture<Void> startFuture = new CompletableActorFuture<>();

    distributedLogstreamCompletableFuture
        .whenComplete(
            (log, error) -> {
              if (error == null) {
                distributedLog = log;
              } else {
                startFuture.completeExceptionally(error);
              }
            })
        .thenApply(log -> this.claimLeaderShip())
        .whenComplete(
            (r, error) -> {
              if (error == null) {
                LOG.info(
                    "Node {} claimed leadership for partition {} successfully",
                    memberId,
                    partitionId);
                startFuture.complete(null);
              } else {
                startFuture.completeExceptionally(error);
              }
            });
    startContext.async(startFuture, true);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    // Close resources used by the local node. This doesn't delete the state of the primitive.
    distributedLog.async().close();
  }

  @Override
  public DistributedLogstreamPartition get() {
    return this;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }
}

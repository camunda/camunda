/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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
import io.zeebe.util.ZbLogger;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public class DistributedLogstreamPartition implements Service<DistributedLogstreamPartition> {
  private static final Logger LOG = new ZbLogger(DistributedLogstreamPartition.class);
  private static final MultiRaftProtocol PROTOCOL =
      MultiRaftProtocol.builder()
          // Maps partitionName to partitionId
          .withPartitioner(DistributedLogstreamName.getInstance())
          .build();
  private final int partitionId;
  private final String partitionName;
  private final String primitiveName;
  private final long currentLeaderTerm;
  private final Injector<Atomix> atomixInjector = new Injector<>();
  private DistributedLogstream distributedLog;
  private Atomix atomix;
  private String memberId;

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
    final CompletableActorFuture<Void> startFuture = new CompletableActorFuture<>();
    this.atomix = atomixInjector.getValue();
    this.memberId = atomix.getMembershipService().getLocalMember().id().id();
    startContext.async(startFuture, true);
    tryStart(startFuture);
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

  private void tryStart(CompletableActorFuture<Void> startFuture) {
    final CompletableFuture<Boolean> leadershipClaim;
    if (distributedLog == null) {
      leadershipClaim = buildPrimitiveAsync().thenCompose(this::onPrimitiveBuilt);
    } else {
      leadershipClaim = this.onPrimitiveBuilt(distributedLog);
    }

    leadershipClaim.whenComplete((nothing, error) -> onLeadershipClaimed(startFuture, error));
  }

  private void onLeadershipClaimed(CompletableActorFuture<Void> startFuture, Throwable error) {
    if (error == null) {
      LOG.debug("Partition {} for node {} claimed leadership", partitionId, memberId);
      startFuture.complete(null);
    }
    if (error != null) {
      LOG.error(
          "Partition {} for node {} failed to start, retrying.", partitionId, memberId, error);
      tryStart(startFuture);
    }
  }

  private CompletableFuture<Boolean> onPrimitiveBuilt(DistributedLogstream distributedLog) {
    this.distributedLog = distributedLog;
    return this.claimLeaderShip();
  }

  private CompletableFuture<DistributedLogstream> buildPrimitiveAsync() {
    return atomix
        .<DistributedLogstreamBuilder, DistributedLogstreamConfig, DistributedLogstream>
            primitiveBuilder(primitiveName, DistributedLogstreamType.instance())
        .withProtocol(PROTOCOL)
        .buildAsync();
  }
}

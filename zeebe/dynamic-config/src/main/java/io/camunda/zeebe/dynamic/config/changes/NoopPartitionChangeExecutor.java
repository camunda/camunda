/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Collection;
import java.util.Map;

public final class NoopPartitionChangeExecutor implements PartitionChangeExecutor {

  @Override
  public ActorFuture<Void> join(
      final int partitionId,
      final Map<MemberId, Integer> membersWithPriority,
      final DynamicPartitionConfig config) {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> leave(final int partitionId) {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> bootstrap(
      final int partitionId,
      final int priority,
      final DynamicPartitionConfig partitionConfig,
      final boolean initializeFromSnapshot) {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> reconfigurePriority(final int partitionId, final int newPriority) {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> forceReconfigure(
      final int partitionId, final Collection<MemberId> members) {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> disableExporter(final int partitionId, final String exporterId) {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> deleteExporter(final int partitionId, final String exporterId) {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> enableExporter(
      final int partitionId,
      final String exporterId,
      final long metadataVersionToUpdate,
      final String initializeFrom) {
    return CompletableActorFuture.completed(null);
  }
}

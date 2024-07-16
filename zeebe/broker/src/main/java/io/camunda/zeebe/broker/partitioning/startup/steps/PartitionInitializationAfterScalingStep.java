/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps;

import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.logstreams.impl.log.SequencedBatch;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.List;

public class PartitionInitializationAfterScalingStep
    implements StartupStep<PartitionStartupContext>, AppendListener {

  private final ActorFuture<Void> startedFuture = new CompletableActorFuture<>();

  @Override
  public String getName() {
    return "Partition Initialization After Scaling";
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(
      final PartitionStartupContext partitionStartupContext) {

    final var storage =
        AtomixLogStorage.ofPartition(
            partitionStartupContext.raftPartition().getServer()::openReader,
            partitionStartupContext.raftPartition().getServer().getAppender().orElseThrow());

    final RecordMetadata metadata =
        new RecordMetadata()
            .intent(DeploymentIntent.REQUEST)
            .valueType(ValueType.DEPLOYMENT)
            .recordType(RecordType.COMMAND);
    final var value = new DeploymentRecord();
    final LogAppendEntry entry = LogAppendEntry.of(metadata, value);
    final BufferWriter batch = new SequencedBatch(1, 1, -1, List.of(entry));
    storage.append(1L, 1L, batch, this);

    final var started = new CompletableActorFuture<PartitionStartupContext>();
    startedFuture.onComplete(
        (ignore, error) -> {
          started.complete(partitionStartupContext);
        });
    return started;
  }

  @Override
  public ActorFuture<PartitionStartupContext> shutdown(
      final PartitionStartupContext partitionStartupContext) {
    return CompletableActorFuture.completed(partitionStartupContext);
  }

  @Override
  public void onCommit(final long index, final long highestPosition) {
    startedFuture.complete(null);
  }
}

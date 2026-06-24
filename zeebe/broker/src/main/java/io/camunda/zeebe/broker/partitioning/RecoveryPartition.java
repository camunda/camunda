/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static io.camunda.zeebe.scheduler.Actor.ACTOR_PROP_PARTITION_ID;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupProcess;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RecoveryPartition {
  private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryPartition.class);

  private final RecoveryPartitionStartupContext context;
  private final StartupProcess<RecoveryPartitionStartupContext> startupProcess;

  private RecoveryPartition(
      final RecoveryPartitionStartupContext context,
      final StartupProcess<RecoveryPartitionStartupContext> startupProcess) {
    this.context = context;
    this.startupProcess = startupProcess;
  }

  static RecoveryPartition recovering(final RecoveryPartitionStartupContext context) {
    final var partitionId = context.partitionId();
    return new RecoveryPartition(
        context,
        new StartupProcess<>(
            Map.of(ACTOR_PROP_PARTITION_ID, String.valueOf(partitionId)), LOGGER, List.of()));
  }

  ActorFuture<RecoveryPartition> start() {
    final var concurrencyControl = context.getConcurrencyControl();
    final var result = concurrencyControl.<RecoveryPartition>createFuture();
    concurrencyControl.run(
        () -> {
          final var startup = startupProcess.startup(concurrencyControl, context);
          concurrencyControl.runOnCompletion(
              startup,
              (ok, error) -> {
                if (error != null) {
                  result.completeExceptionally(error);
                } else {
                  result.complete(this);
                }
              });
        });
    return result;
  }

  ActorFuture<RecoveryPartition> stop() {
    final var concurrencyControl = context.getConcurrencyControl();
    final var result = concurrencyControl.<RecoveryPartition>createFuture();
    concurrencyControl.run(
        () -> {
          final var shutdown = startupProcess.shutdown(concurrencyControl, context);
          concurrencyControl.runOnCompletion(
              shutdown,
              (ok, error) -> {
                if (error != null) {
                  result.completeExceptionally(error);
                } else {
                  result.complete(this);
                }
              });
        });
    return result;
  }
}

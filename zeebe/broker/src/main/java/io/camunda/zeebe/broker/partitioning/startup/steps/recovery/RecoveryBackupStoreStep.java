/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps.recovery;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.broker.partitioning.RecoveryPartitionStartupContext;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;

/**
 * Creates the {@link BackupStore} from the broker backup configuration. The store creation mirrors
 * {@code BackupStoreTransitionStep} but is read-only: it is only used to serve backup status, list
 * and range queries during recovery. A {@code NONE} store type leaves the store {@code null}, which
 * disables the backup API in recovery mode.
 */
public record RecoveryBackupStoreStep(PartitionId partitionId)
    implements StartupStep<RecoveryPartitionStartupContext> {

  @Override
  public String getName() {
    return "Recovery Partition %d - Backup Store".formatted(partitionId.number());
  }

  @Override
  public ActorFuture<RecoveryPartitionStartupContext> startup(
      final RecoveryPartitionStartupContext context) {
    final var backupCfg = context.getBrokerCfg().getData().getBackup();
    final BackupStore store;
    try {
      store = BackupCfg.BackupStoreFactory.createStore(backupCfg);
    } catch (final Exception e) {
      return CompletableActorFuture.completedExceptionally(e);
    }
    return CompletableActorFuture.completed(context.setBackupStore(store));
  }

  @Override
  public ActorFuture<RecoveryPartitionStartupContext> shutdown(
      final RecoveryPartitionStartupContext context) {
    final var store = context.getBackupStore();
    if (store == null) {
      return CompletableActorFuture.completed(context.setBackupStore(null));
    }
    final ActorFuture<RecoveryPartitionStartupContext> closed =
        context.getConcurrencyControl().createFuture();
    store
        .closeAsync()
        .whenCompleteAsync(
            (ignore, error) -> {
              if (error != null) {
                closed.completeExceptionally(error);
              } else {
                closed.complete(context.setBackupStore(null));
              }
            },
            context.getConcurrencyControl());
    return closed;
  }
}

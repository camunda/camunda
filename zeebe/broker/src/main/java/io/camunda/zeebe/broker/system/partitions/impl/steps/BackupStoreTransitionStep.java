/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.azure.AzureBackupStore;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupStore;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.broker.system.InvalidConfigurationException;
import io.camunda.zeebe.broker.system.configuration.backup.AzureBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.broker.system.configuration.backup.FilesystemBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.S3BackupStoreConfig;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

public final class BackupStoreTransitionStep implements PartitionTransitionStep {

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final BackupStore backupStore = context.getBackupStore();
    if (backupStore != null && shouldCloseOnTransition(context.getCurrentRole(), targetRole)) {
      final ActorFuture<Void> closed = context.getConcurrencyControl().createFuture();
      backupStore
          .closeAsync()
          .thenAcceptAsync(
              ignore -> {
                context.setBackupStore(null);
                context.setCheckpointProcessor(null);
                closed.complete(null);
              },
              // updates to context must execute on this actor
              context.getConcurrencyControl()::run);
      return closed;
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final ActorFuture<Void> installed = context.getConcurrencyControl().createFuture();

    if (shouldInstallOnTransition(context.getCurrentRole(), targetRole)
        || (context.getBackupStore() == null && targetRole != Role.INACTIVE)) {
      final var backupCfg = context.getBrokerCfg().getData().getBackup();
      final BackupStore store;
      try {
        store =
            switch (backupCfg.getStore()) {
              case NONE -> null; // No backup store is installed. BackupManager can handle this case
              case S3 -> createS3Store(backupCfg);
              case GCS -> createGcsStore(backupCfg);
              case AZURE -> createAzureStore(backupCfg);
              case FILESYSTEM -> createFilesystemStore(backupCfg);
            };
      } catch (final Exception e) {
        installed.completeExceptionally("Failed to create backup store.", e);
        return installed;
      }

      if (backupCfg.isRequired()) {
        if (store == null) {
          installed.completeExceptionally(
              new InvalidConfigurationException(
                  "Backup store is required but no backup store type is configured."));
          return installed;
        }

        store
            .verifyConnection()
            .whenCompleteAsync(
                (ignore, err) -> {
                  if (err != null) {
                    context.setBackupStore(null);
                    store.closeAsync();
                    installed.completeExceptionally(err);
                  } else {
                    context.setBackupStore(store);
                    installed.complete(null);
                  }
                },
                context.getConcurrencyControl());
      } else {
        context.setBackupStore(store);
        installed.complete(null);
      }
    } else {
      installed.complete(null);
    }
    return installed;
  }

  @Override
  public String getName() {
    return "BackupStore";
  }

  private static BackupStore createS3Store(final BackupCfg backupCfg) {
    final var storeConfig = S3BackupStoreConfig.toStoreConfig(backupCfg.getS3());
    return S3BackupStore.of(storeConfig);
  }

  private static BackupStore createGcsStore(final BackupCfg backupCfg) {
    final var brokerGcsConfig = backupCfg.getGcs();
    final var storeGcsConfig = GcsBackupStoreConfig.toStoreConfig(brokerGcsConfig);
    return GcsBackupStore.of(storeGcsConfig);
  }

  private static BackupStore createAzureStore(final BackupCfg backupCfg) {
    final var brokerAzureConfig = backupCfg.getAzure();
    final var storeAzureConfig = AzureBackupStoreConfig.toStoreConfig(brokerAzureConfig);
    return AzureBackupStore.of(storeAzureConfig);
  }

  private static BackupStore createFilesystemStore(final BackupCfg backupCfg) {
    final var brokerFilesystemConfig = backupCfg.getFilesystem();
    final var storeFilesystemConfig =
        FilesystemBackupStoreConfig.toStoreConfig(brokerFilesystemConfig);
    return FilesystemBackupStore.of(storeFilesystemConfig);
  }

  private boolean shouldInstallOnTransition(final Role currentRole, final Role targetRole) {
    return targetRole == Role.LEADER
        || (targetRole == Role.FOLLOWER && currentRole != Role.CANDIDATE)
        || (targetRole == Role.CANDIDATE && currentRole != Role.FOLLOWER);
  }

  private boolean shouldCloseOnTransition(final Role currentRole, final Role targetRole) {
    return shouldInstallOnTransition(currentRole, targetRole) || targetRole == Role.INACTIVE;
  }
}

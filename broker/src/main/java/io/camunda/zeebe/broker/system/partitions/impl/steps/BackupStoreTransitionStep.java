/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.gcs.GcsBackupConfig;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg;
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
      switch (backupCfg.getStore()) {
        case NONE -> {
          // No backup store is installed. BackupManager can handle this case
          context.setBackupStore(null);
          installed.complete(null);
        }
        case S3 -> installS3Store(context, backupCfg, installed);
        case GCS -> installGcsStore(context, backupCfg, installed);
        default -> installed.completeExceptionally(
            new IllegalArgumentException(
                "Unknown backup store type %s".formatted(backupCfg.getStore())));
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

  private static void installS3Store(
      final PartitionTransitionContext context,
      final BackupStoreCfg backupCfg,
      final ActorFuture<Void> installed) {
    try {
      final var storeConfig = S3BackupStoreConfig.toStoreConfig(backupCfg.getS3());
      final var backupStore = new S3BackupStore(storeConfig);
      context.setBackupStore(backupStore);
      installed.complete(null);
    } catch (final Exception error) {
      installed.completeExceptionally("Failed to create backup store", error);
    }
  }

  private static void installGcsStore(
      final PartitionTransitionContext context,
      final BackupStoreCfg backupCfg,
      final ActorFuture<Void> installed) {
    try {
      final var gcsConfig = backupCfg.getGcs();
      final var storeConfig =
          new GcsBackupConfig.Builder()
              .withBucketName(gcsConfig.getBucketName())
              .withBasePath(gcsConfig.getBasePath());
      final var authenticated =
          switch (gcsConfig.getAuth()) {
            case NONE -> storeConfig.withoutAuthentication();
            case AUTO -> storeConfig.withAutoAuthentication();
          };
      final var gcsStore = new GcsBackupStore(authenticated.build());
      context.setBackupStore(gcsStore);
      installed.complete(null);
    } catch (final Exception error) {
      installed.completeExceptionally("Failed to create backup store", error);
    }
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

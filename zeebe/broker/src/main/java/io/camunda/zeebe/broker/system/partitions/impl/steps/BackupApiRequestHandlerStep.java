/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.broker.transport.backupapi.BackupApiRequestHandler;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

public final class BackupApiRequestHandlerStep implements PartitionTransitionStep {

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final BackupApiRequestHandler backupApiRequestHandler = context.getBackupApiRequestHandler();
    if (backupApiRequestHandler != null) {
      context.getDiskSpaceUsageMonitor().removeDiskUsageListener(backupApiRequestHandler);
      final var closeFuture = backupApiRequestHandler.closeAsync();
      context.setBackupApiRequestHandler(null);
      return closeFuture;
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    // Only installed during the leader
    if (targetRole == Role.LEADER) {
      return installRequestHandler(context);
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "BackupApiRequestHandler";
  }

  private ActorFuture<Void> installRequestHandler(final PartitionTransitionContext context) {
    final ActorFuture<Void> installed = context.getConcurrencyControl().createFuture();
    final var writerFuture = context.getLogStream().newLogStreamWriter();
    writerFuture.onComplete(
        (logStreamRecordWriter, error) -> {
          if (error == null) {
            createBackupApiRequestHandler(context, installed, logStreamRecordWriter);
          } else {
            installed.completeExceptionally(error);
          }
        });

    return installed;
  }

  private void createBackupApiRequestHandler(
      final PartitionTransitionContext context,
      final ActorFuture<Void> installed,
      final LogStreamWriter logStreamWriter) {
    final var isBackupEnabled =
        !context.getBrokerCfg().getData().getBackup().getStore().equals(BackupStoreType.NONE);
    final var requestHandler =
        new BackupApiRequestHandler(
            context.getGatewayBrokerTransport(),
            logStreamWriter,
            context.getBackupManager(),
            context.getPartitionId(),
            isBackupEnabled);
    context.getActorSchedulingService().submitActor(requestHandler).onComplete(installed);
    installed.onComplete(
        (ignore, error) -> {
          if (error == null) {
            context.setBackupApiRequestHandler(requestHandler);
            context.getDiskSpaceUsageMonitor().addDiskUsageListener(requestHandler);
          }
        });
  }
}

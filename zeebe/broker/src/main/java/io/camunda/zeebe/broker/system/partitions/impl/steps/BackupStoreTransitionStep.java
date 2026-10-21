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
import io.camunda.zeebe.broker.eventplane.EventPlaneClusterKey;
import io.camunda.zeebe.broker.eventplane.EventPlaneStores;
import io.camunda.zeebe.broker.eventplane.PublisherService;
import io.camunda.zeebe.broker.system.InvalidConfigurationException;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.restore.EventPlaneLogBackupStore;
import io.camunda.zeebe.restore.EventPlaneLogBackupStore.PublishingHistory;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

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
      final BackupStore created;
      try {
        created = BackupCfg.BackupStoreFactory.createStore(backupCfg);
      } catch (final Exception e) {
        installed.completeExceptionally("Failed to create backup store.", e);
        return installed;
      }
      final BackupStore store;
      try {
        store = keepLogInEventPlane(created, context);
      } catch (final Exception e) {
        if (created != null) {
          created.closeAsync();
        }
        installed.completeExceptionally(
            "Failed to keep the backups' log in the Event Plane's bucket.", e);
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

  /**
   * Wraps the store, so that its backups keep their log in the Event Plane's bucket instead of
   * copying the journal's segments, if the Event Plane and its {@code backupLog} are on. Returns
   * the store as it is if not, or null if there is none.
   */
  private static @Nullable BackupStore keepLogInEventPlane(
      final @Nullable BackupStore store, final PartitionTransitionContext context) {
    final var eventPlane = context.getBrokerCfg().getExperimental().getEventPlane();
    if (store == null || !eventPlane.isEnabled() || !eventPlane.isBackupLog()) {
      return store;
    }
    // the same cluster the publisher names, so that a backup's log is found where it was published
    final var clusterKey = EventPlaneClusterKey.of(context);
    return EventPlaneLogBackupStore.wrap(
        store,
        EventPlaneStores.open(eventPlane.getStore()),
        clusterKey,
        context.partitionId().group(),
        eventPlane.getBackupLogTimeout(),
        publishing(context));
  }

  /**
   * The history of the partition's publisher, read from the context when asked: the Event Plane
   * step may install the publisher after this step, and replaces it when the leadership changes.
   */
  static PublishingHistory publishing(final PartitionTransitionContext context) {
    return partition ->
        Optional.ofNullable(context.getEventPlanePublisher())
            .flatMap(PublisherService::publishedHistory);
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

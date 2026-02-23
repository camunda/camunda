/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.management;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.azure.AzureBackupStore;
import io.camunda.zeebe.backup.client.api.BackupRequestHandler;
import io.camunda.zeebe.backup.common.CheckpointIdGenerator;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupStore;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.backup.retention.BackupRetention;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.backup.schedule.CheckpointScheduler;
import io.camunda.zeebe.backup.schedule.Schedule;
import io.camunda.zeebe.backup.schedule.Schedule.IntervalSchedule;
import io.camunda.zeebe.backup.schedule.Schedule.NoneSchedule;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.system.configuration.backup.AzureBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.broker.system.configuration.backup.FilesystemBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.S3BackupStoreConfig;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckpointSchedulingService extends Actor implements ClusterMembershipEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointSchedulingService.class);

  private final ClusterMembershipService membershipService;
  private final BackupCfg backupCfg;
  private final ActorSchedulingService actorScheduler;
  private final MeterRegistry meterRegistry;
  private final BrokerClient brokerClient;
  private CheckpointScheduler checkpointScheduler;
  private final BackupRequestHandler backupRequestHandler;
  private BackupRetention backupRetentionJob;

  public CheckpointSchedulingService(
      final ClusterMembershipService membershipService,
      final ActorSchedulingService actorScheduler,
      final BackupCfg backupCfg,
      final BrokerClient brokerClient,
      final MeterRegistry meterRegistry) {
    this.membershipService = membershipService;
    this.actorScheduler = actorScheduler;
    this.backupCfg = backupCfg;
    this.meterRegistry = meterRegistry;
    this.brokerClient = brokerClient;
    backupRequestHandler =
        new BackupRequestHandler(brokerClient, new CheckpointIdGenerator(backupCfg.getOffset()));
  }

  @Override
  protected void onActorStarting() {
    membershipService.addListener(this);
    Schedule checkpointSchedule = null;
    Schedule backupSchedule = null;
    if (backupCfg.getCheckpointInterval() != null && !backupCfg.getCheckpointInterval().isZero()) {
      checkpointSchedule = new IntervalSchedule(backupCfg.getCheckpointInterval());
      LOG.info("Checkpoint scheduler initialized with interval {}", checkpointSchedule);
    }
    if (backupCfg.getSchedule() != null && !(backupCfg.getSchedule() instanceof NoneSchedule)) {
      backupSchedule = backupCfg.getSchedule();
      LOG.info("Backup scheduler initialized with interval {}", backupSchedule);
    }

    final var retentionCfg = backupCfg.getRetention();
    if (shouldRegisterRetentionJob()) {
      final var backupStore = buildBackupStore(backupCfg);
      backupRetentionJob =
          new BackupRetention(
              backupStore,
              retentionCfg.getCleanupSchedule(),
              retentionCfg.getWindow(),
              brokerClient.getTopologyManager(),
              meterRegistry);
      LOG.info(
          "Backup retention initialized with cleanup schedule {}",
          retentionCfg.getCleanupSchedule());
    }

    if (checkpointSchedule != null || backupSchedule != null) {
      checkpointScheduler =
          new CheckpointScheduler(
              checkpointSchedule, backupSchedule, backupRequestHandler, meterRegistry);
    }
  }

  @Override
  protected void onActorStarted() {
    checkedStartScheduler();
  }

  @Override
  protected void onActorCloseRequested() {
    membershipService.removeListener(this);
    if (isSchedulerActive()) {
      final List<ActorFuture<Void>> shutdownFutures = new ArrayList<>();
      shutdownFutures.add(checkpointScheduler.closeAsync());
      if (backupRetentionJob != null) {
        shutdownFutures.add(backupRetentionJob.closeAsync());
      }
      actor.runOnCompletion(
          shutdownFutures,
          (error) -> {
            if (error != null) {
              LOG.error("Failed to close checkpoint creator actor", error);
            }
          });
    }
  }

  @Override
  public boolean isRelevant(final ClusterMembershipEvent event) {
    return event.type() == Type.MEMBER_ADDED || event.type() == Type.MEMBER_REMOVED;
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    switch (event.type()) {
      case MEMBER_ADDED -> {
        checkedStopScheduler();
        checkedStartScheduler();
      }
      case MEMBER_REMOVED -> checkedStartScheduler();
      default -> {}
    }
  }

  private void checkedStopScheduler() {
    if (shouldStopSchedulers() && isSchedulerActive()) {
      checkpointScheduler.close();
      if (backupRetentionJob != null) {
        backupRetentionJob.close();
      }
    }
  }

  private void checkedStartScheduler() {
    if (shouldStartSchedulers() && isSchedulerInactive()) {
      actorScheduler.submitActor(checkpointScheduler, SchedulingHints.ioBound());
      if (backupRetentionJob != null) {
        actorScheduler.submitActor(backupRetentionJob, SchedulingHints.ioBound());
      }
    }
  }

  private boolean isSchedulerActive() {
    return checkpointScheduler != null && !checkpointScheduler.isActorClosed();
  }

  private boolean isSchedulerInactive() {
    return checkpointScheduler != null && checkpointScheduler.isActorClosed();
  }

  private boolean shouldStartSchedulers() {
    final var localMemberId = membershipService.getLocalMember().id();
    return membershipService.getMembers().stream()
        .min(Comparator.comparing(Member::id, MemberId::compareTo))
        .map(lowestMember -> lowestMember.id().equals(localMemberId))
        .orElse(false);
  }

  private boolean shouldStopSchedulers() {
    final var localMemberId = membershipService.getLocalMember().id();
    return membershipService.getMembers().stream()
        .min(Comparator.comparing(Member::id, MemberId::compareTo))
        .map(lowestMember -> !lowestMember.id().equals(localMemberId))
        .orElse(false);
  }

  private boolean shouldRegisterRetentionJob() {
    final var retentionCfg = backupCfg.getRetention();
    return retentionCfg.getWindow() != null
        && !retentionCfg.getWindow().isZero()
        && retentionCfg.getCleanupSchedule() != null
        && !(retentionCfg.getCleanupSchedule() instanceof NoneSchedule);
  }

  private BackupStore buildBackupStore(final BackupCfg backupCfg) {
    final var store = backupCfg.getStore();
    return switch (store) {
      case S3 -> buildS3BackupStore(backupCfg);
      case GCS -> buildGcsBackupStore(backupCfg);
      case AZURE -> buildAzureBackupStore(backupCfg);
      case FILESYSTEM -> buildFilesystemBackupStore(backupCfg);
      case NONE ->
          throw new IllegalArgumentException(
              "No backup store configured, cannot restore from backup.");
    };
  }

  private static BackupStore buildS3BackupStore(final BackupCfg backupCfg) {
    final var storeConfig = S3BackupStoreConfig.toStoreConfig(backupCfg.getS3());
    return S3BackupStore.of(storeConfig);
  }

  private static BackupStore buildGcsBackupStore(final BackupCfg backupCfg) {
    final var storeConfig = GcsBackupStoreConfig.toStoreConfig(backupCfg.getGcs());
    return GcsBackupStore.of(storeConfig);
  }

  private static BackupStore buildAzureBackupStore(final BackupCfg backupCfg) {
    final var storeConfig = AzureBackupStoreConfig.toStoreConfig(backupCfg.getAzure());
    return AzureBackupStore.of(storeConfig);
  }

  private static BackupStore buildFilesystemBackupStore(final BackupCfg backupCfg) {
    final var storeConfig = FilesystemBackupStoreConfig.toStoreConfig(backupCfg.getFilesystem());
    return FilesystemBackupStore.of(storeConfig);
  }
}

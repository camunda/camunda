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
import io.camunda.zeebe.backup.schedule.CheckpointScheduler;
import io.camunda.zeebe.backup.schedule.Schedule;
import io.camunda.zeebe.backup.schedule.Schedule.IntervalSchedule;
import io.camunda.zeebe.backup.schedule.Schedule.NoneSchedule;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckpointSchedulingService extends Actor implements ClusterMembershipEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointSchedulingService.class);

  private final ClusterMembershipService membershipService;
  private final BackupCfg backupCfg;
  private final BrokerClient brokerClient;
  private final ActorSchedulingService actorScheduler;
  private final MeterRegistry meterRegistry;
  private CheckpointScheduler checkpointScheduler;

  public CheckpointSchedulingService(
      final ClusterMembershipService membershipService,
      final ActorSchedulingService actorScheduler,
      final BackupCfg backupCfg,
      final BrokerClient brokerClient,
      final MeterRegistry meterRegistry) {
    this.membershipService = membershipService;
    this.actorScheduler = actorScheduler;
    this.backupCfg = backupCfg;
    this.brokerClient = brokerClient;
    this.meterRegistry = meterRegistry;
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

    checkpointScheduler =
        new CheckpointScheduler(checkpointSchedule, backupSchedule, brokerClient, meterRegistry);
  }

  @Override
  protected void onActorStarted() {
    if (shouldStartSchedulers()) {
      actorScheduler.submitActor(checkpointScheduler);
    }
  }

  @Override
  protected void onActorCloseRequested() {
    membershipService.removeListener(this);
    if (isSchedulerActive()) {
      actor.runOnCompletion(
          checkpointScheduler.closeAsync(),
          (ok, error) -> {
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
    }
  }

  private void checkedStartScheduler() {
    if (shouldStartSchedulers() && isSchedulerInactive()) {
      actorScheduler.submitActor(checkpointScheduler);
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
}

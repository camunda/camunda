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
import io.camunda.zeebe.backup.client.api.BackupRequestHandler;
import io.camunda.zeebe.backup.common.CheckpointIdGenerator;
import io.camunda.zeebe.backup.retention.BackupRetention;
import io.camunda.zeebe.backup.schedule.CheckpointScheduler;
import io.camunda.zeebe.backup.schedule.Schedule;
import io.camunda.zeebe.backup.schedule.Schedule.IntervalSchedule;
import io.camunda.zeebe.backup.schedule.Schedule.NoneSchedule;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg.BackupStoreFactory;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the scheduled checkpoint/backup and backup retention jobs of every physical tenant this
 * broker runs. Each tenant brings its own backup configuration, so it gets its own {@link
 * CheckpointScheduler} and {@link BackupRetention} actors, targeting only that tenant's partitions
 * and writing to only that tenant's backup store.
 *
 * <p>The jobs are cluster-wide singletons: they run on the member with the lowest id, and all
 * tenants' jobs are started and stopped together as membership changes.
 */
public class CheckpointSchedulingService extends Actor implements ClusterMembershipEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointSchedulingService.class);

  private final ClusterMembershipService membershipService;
  private final Map<String, BackupCfg> backupCfgByPhysicalTenant;
  private final ActorSchedulingService actorScheduler;
  private final MeterRegistry meterRegistry;
  private final BrokerClient brokerClient;
  private final List<PhysicalTenantSchedulers> tenantSchedulers = new ArrayList<>();

  public CheckpointSchedulingService(
      final ClusterMembershipService membershipService,
      final ActorSchedulingService actorScheduler,
      final Map<String, BackupCfg> backupCfgByPhysicalTenant,
      final BrokerClient brokerClient,
      final MeterRegistry meterRegistry) {
    this.membershipService = membershipService;
    this.actorScheduler = actorScheduler;
    this.backupCfgByPhysicalTenant = new LinkedHashMap<>(backupCfgByPhysicalTenant);
    this.meterRegistry = meterRegistry;
    this.brokerClient = brokerClient;
  }

  @Override
  protected void onActorStarting() {
    membershipService.addListener(this);
    backupCfgByPhysicalTenant.forEach(
        (physicalTenantId, backupCfg) -> {
          final var schedulers = createSchedulers(physicalTenantId, backupCfg);
          if (schedulers != null) {
            tenantSchedulers.add(schedulers);
          }
        });
  }

  @Override
  protected void onActorStarted() {
    checkedStartSchedulers();
  }

  @Override
  protected void onActorCloseRequested() {
    membershipService.removeListener(this);
<<<<<<< HEAD
    final List<ActorFuture<Void>> shutdownFutures =
        schedulerActors().stream()
            .filter(actor -> !actor.isActorClosed())
            .map(Actor::closeAsync)
            .collect(Collectors.toCollection(ArrayList::new));

=======
    final List<ActorFuture<Void>> shutdownFutures = new ArrayList<>();
    for (final var schedulerActor : schedulerActors()) {
      if (!schedulerActor.isActorClosed()) {
        shutdownFutures.add(schedulerActor.closeAsync());
      }
    }
>>>>>>> d3fee4f0 (fix: allow retention job to be registered without the checkpoint scheduler)
    actor.runOnCompletion(
        shutdownFutures,
        (error) -> {
          if (error != null) {
            LOG.error("Failed to close checkpoint scheduling actors", error);
          }
<<<<<<< HEAD
          closeMeterRegistries();
=======
>>>>>>> d3fee4f0 (fix: allow retention job to be registered without the checkpoint scheduler)
        });
  }

  @Override
  public boolean isRelevant(final ClusterMembershipEvent event) {
    return event.type() == Type.MEMBER_ADDED || event.type() == Type.MEMBER_REMOVED;
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    switch (event.type()) {
      case MEMBER_ADDED -> {
        checkedStopSchedulers();
        checkedStartSchedulers();
      }
      case MEMBER_REMOVED -> checkedStartSchedulers();
      default -> {}
    }
  }

<<<<<<< HEAD
  private @Nullable PhysicalTenantSchedulers createSchedulers(
      final String physicalTenantId, final BackupCfg backupCfg) {
    Schedule checkpointSchedule = null;
    Schedule backupSchedule = null;
    if (backupCfg.getCheckpointInterval() != null && !backupCfg.getCheckpointInterval().isZero()) {
      checkpointSchedule = new IntervalSchedule(backupCfg.getCheckpointInterval());
    }
    if (!(backupCfg.getSchedule() instanceof NoneSchedule)) {
      backupSchedule = backupCfg.getSchedule();
=======
  private void checkedStopScheduler() {
    if (!shouldStopSchedulers()) {
      return;
    }
    for (final var schedulerActor : schedulerActors()) {
      if (!schedulerActor.isActorClosed()) {
        schedulerActor.close();
      }
    }
  }

  private void checkedStartScheduler() {
    if (!shouldStartSchedulers()) {
      return;
    }
    for (final var schedulerActor : schedulerActors()) {
      if (schedulerActor.isActorClosed()) {
        actorScheduler.submitActor(schedulerActor, SchedulingHints.ioBound());
      }
>>>>>>> d3fee4f0 (fix: allow retention job to be registered without the checkpoint scheduler)
    }
    final var withRetention = shouldRegisterRetentionJob(backupCfg);

    if (checkpointSchedule == null && backupSchedule == null && !withRetention) {
      return null;
    }

    // Every tenant reports its metrics under the same names, so they are told apart by the tenant
    // tag that this wrapped registry stamps on all of them.
    final var tenantMeterRegistry =
        MicrometerUtil.wrap(
            meterRegistry, Tags.of(PartitionKeyNames.PHYSICAL_TENANT.asString(), physicalTenantId));

    CheckpointScheduler checkpointScheduler = null;
    if (checkpointSchedule != null || backupSchedule != null) {
      final var backupRequestHandler =
          new BackupRequestHandler(brokerClient, new CheckpointIdGenerator(backupCfg.getOffset()));
      checkpointScheduler =
          new CheckpointScheduler(
              physicalTenantId,
              checkpointSchedule,
              backupSchedule,
              backupRequestHandler,
              brokerClient.getTopologyManager(),
              tenantMeterRegistry);
    }

    BackupRetention backupRetentionJob = null;
    if (withRetention) {
      final var retentionCfg = backupCfg.getRetention();
      final var backupStore = BackupStoreFactory.createStore(backupCfg);
      if (backupStore == null) {
        throw new IllegalStateException(
            "No backup store configured for physical tenant " + physicalTenantId);
      }
      backupRetentionJob =
          new BackupRetention(
              physicalTenantId,
              () -> backupStore,
              brokerClient,
              retentionCfg.getCleanupSchedule(),
              retentionCfg.getWindow(),
              brokerClient.getTopologyManager(),
              tenantMeterRegistry);
    }

    return new PhysicalTenantSchedulers(
        physicalTenantId, tenantMeterRegistry, checkpointScheduler, backupRetentionJob);
  }

<<<<<<< HEAD
  private void checkedStopSchedulers() {
    if (!shouldStopSchedulers()) {
      return;
    }
    schedulerActors().stream().filter(actor -> !actor.isActorClosed()).forEach(Actor::close);
  }

  private void checkedStartSchedulers() {
    if (!shouldStartSchedulers()) {
      return;
    }
    schedulerActors().stream()
        .filter(Actor::isActorClosed)
        .forEach(actor -> actorScheduler.submitActor(actor, SchedulingHints.ioBound()));
  }

  /** All physical tenants' scheduling actors, in configuration order. */
  @VisibleForTesting
  List<Actor> schedulerActors() {
    return tenantSchedulers.stream().flatMap(schedulers -> schedulers.actors().stream()).toList();
  }

  private void closeMeterRegistries() {
    tenantSchedulers.forEach(schedulers -> MicrometerUtil.close(schedulers.meterRegistry()));
=======
  private List<Actor> schedulerActors() {
    final List<Actor> actors = new ArrayList<>(2);
    if (checkpointScheduler != null) {
      actors.add(checkpointScheduler);
    }
    if (backupRetentionJob != null) {
      actors.add(backupRetentionJob);
    }
    return actors;
>>>>>>> d3fee4f0 (fix: allow retention job to be registered without the checkpoint scheduler)
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

  private boolean shouldRegisterRetentionJob(final BackupCfg backupCfg) {
    final var retentionCfg = backupCfg.getRetention();
    return retentionCfg.getWindow() != null
        && !retentionCfg.getWindow().isZero()
        && retentionCfg.getCleanupSchedule() != null
        && !(retentionCfg.getCleanupSchedule() instanceof NoneSchedule);
  }

  /**
   * The scheduling actors of a single physical tenant, along with the registry they report to. A
   * tenant may have only one of the two, depending on what its backup configuration asks for.
   */
  private record PhysicalTenantSchedulers(
      String physicalTenantId,
      MeterRegistry meterRegistry,
      @Nullable CheckpointScheduler checkpointScheduler,
      @Nullable BackupRetention backupRetentionJob) {

    List<Actor> actors() {
      final List<Actor> actors = new ArrayList<>(2);
      if (checkpointScheduler != null) {
        actors.add(checkpointScheduler);
      }
      if (backupRetentionJob != null) {
        actors.add(backupRetentionJob);
      }
      return actors;
    }
  }
}

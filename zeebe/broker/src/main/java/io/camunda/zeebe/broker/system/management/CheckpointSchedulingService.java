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
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Topology;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.azure.AzureBackupStore;
import io.camunda.zeebe.backup.client.api.BackupRequestHandler;
import io.camunda.zeebe.backup.common.CheckpointIdGenerator;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupStore;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.backup.retention.BackupRetention;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.backup.schedule.CheckpointTriggerJobWorker;
import io.camunda.zeebe.backup.schedule.RetentionTriggerJobWorker;
import io.camunda.zeebe.backup.schedule.Schedule;
import io.camunda.zeebe.backup.schedule.Schedule.CronSchedule;
import io.camunda.zeebe.backup.schedule.Schedule.IntervalSchedule;
import io.camunda.zeebe.backup.schedule.Schedule.NoneSchedule;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.system.configuration.backup.AzureBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.broker.system.configuration.backup.FilesystemBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.S3BackupStoreConfig;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckpointSchedulingService extends Actor implements ClusterMembershipEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointSchedulingService.class);
  private static final String MARKER_PROCESS_ID = "marker_checkpoint_scheduler";
  private static final String BACKUP_PROCESS_ID = "backup_checkpoint_scheduler";
  private static final String RETENTION_PROCESS_ID = "retention_cleanup_scheduler";
  private static final String RETENTION_JOB_TYPE = "retention-trigger";
  private static final URI CAMUNDA_REST_ADDRESS = URI.create("http://localhost:8080");
  private static final Duration TOPOLOGY_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration RETRY_INTERVAL = Duration.ofSeconds(2);

  private final ClusterMembershipService membershipService;
  private final BackupCfg backupCfg;
  private final ActorSchedulingService actorScheduler;
  private final MeterRegistry meterRegistry;
  private final BrokerClient brokerClient;
  private final BackupRequestHandler backupRequestHandler;
  private BackupRetention backupRetentionJob;
  private CamundaClient camundaClient;
  private CheckpointTriggerJobWorker checkpointTriggerWorker;
  private RetentionTriggerJobWorker retentionTriggerWorker;
  private boolean markerEnabled;
  private boolean backupEnabled;
  private boolean retentionEnabled;

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
    markerEnabled =
        backupCfg.getCheckpointInterval() != null && !backupCfg.getCheckpointInterval().isZero();
    backupEnabled =
        backupCfg.getSchedule() != null && !(backupCfg.getSchedule() instanceof NoneSchedule);
    retentionEnabled = isRetentionEnabled();

    if (markerEnabled) {
      LOG.info("Marker checkpoints enabled with interval {}", backupCfg.getCheckpointInterval());
    }
    if (backupEnabled) {
      LOG.info("Backup checkpoints enabled with schedule {}", backupCfg.getSchedule());
    }
    if (retentionEnabled) {
      LOG.info(
          "Retention cleanup enabled with schedule {}",
          backupCfg.getRetention().getCleanupSchedule());
      final var retentionCfg = backupCfg.getRetention();
      backupRetentionJob =
          new BackupRetention(
              buildBackupStore(backupCfg),
              brokerClient,
              retentionCfg.getCleanupSchedule(),
              retentionCfg.getWindow(),
              brokerClient.getTopologyManager(),
              meterRegistry);
    }

    if (markerEnabled || backupEnabled || retentionEnabled) {
      camundaClient =
          CamundaClient.newClientBuilder()
              .restAddress(CAMUNDA_REST_ADDRESS)
              .preferRestOverGrpc(true)
              .build();
    }
    if (markerEnabled || backupEnabled) {
      checkpointTriggerWorker = new CheckpointTriggerJobWorker(camundaClient, backupRequestHandler);
      checkpointTriggerWorker.start();
    }
    if (retentionEnabled) {
      retentionTriggerWorker = new RetentionTriggerJobWorker(camundaClient, backupRetentionJob);
      retentionTriggerWorker.start();
    }
  }

  @Override
  protected void onActorStarted() {
    checkedStartScheduler();
  }

  @Override
  protected void onActorCloseRequested() {
    membershipService.removeListener(this);
    final List<ActorFuture<Void>> shutdownFutures = new ArrayList<>();
    if (backupRetentionJob != null) {
      shutdownFutures.add(backupRetentionJob.closeAsync());
    }
    if (checkpointTriggerWorker != null) {
      checkpointTriggerWorker.close();
    }
    if (retentionTriggerWorker != null) {
      retentionTriggerWorker.close();
    }
    if (camundaClient != null) {
      camundaClient.close();
    }
    if (!shutdownFutures.isEmpty()) {
      actor.runOnCompletion(
          shutdownFutures,
          (error) -> {
            if (error != null) {
              LOG.error("Failed to close checkpoint scheduling service", error);
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
    if (shouldStopSchedulers() && backupRetentionJob != null) {
      backupRetentionJob.close();
    }
  }

  /**
   * On coordinator activation: deploy the timer-driven scheduler BPMNs with config-derived cycles
   * (so the timer subscriptions reflect the broker's {@link BackupCfg}), then submit the {@link
   * BackupRetention} actor so the retention worker can call its {@code triggerRetention()}. The
   * BPMNs' timer-start events create PIs automatically once deployed — we never call {@code
   * newCreateInstanceCommand} on them (timer-start processes reject {@code CREATE} with
   * INVALID_STATE).
   */
  private void checkedStartScheduler() {
    if (!shouldStartSchedulers()) {
      return;
    }
    deploySchedulerBpmnsAfterClusterReady();
    if (backupRetentionJob != null) {
      actorScheduler.submitActor(backupRetentionJob, SchedulingHints.ioBound());
    }
  }

  private void deploySchedulerBpmnsAfterClusterReady() {
    if (camundaClient == null || !(markerEnabled || backupEnabled || retentionEnabled)) {
      return;
    }
    Thread.ofVirtual().name("scheduler-bpmn-deployer").start(this::awaitClusterAndDeploy);
  }

  private void awaitClusterAndDeploy() {
    int attempt = 0;
    while (!Thread.currentThread().isInterrupted()) {
      attempt++;
      if (isClusterReady()) {
        LOG.info(
            "Cluster topology reachable after {} attempt(s), deploying scheduler BPMNs", attempt);
        deploySchedulerBpmns();
        return;
      }
      try {
        Thread.sleep(RETRY_INTERVAL.toMillis());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        LOG.warn("Scheduler BPMN deploy interrupted before cluster became ready");
        return;
      }
    }
  }

  private boolean isClusterReady() {
    try {
      final Topology topology =
          camundaClient
              .newTopologyRequest()
              .send()
              .get(TOPOLOGY_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      return topology != null && !topology.getBrokers().isEmpty();
    } catch (final Exception e) {
      LOG.debug("Topology check failed: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Builds and deploys one BPMN process per enabled schedule, with the timer cycle expression
   * derived from the corresponding {@link BackupCfg} entry. Re-deploying simply creates a new
   * version, so this is safe to call on every coordinator activation.
   */
  private void deploySchedulerBpmns() {
    final List<BpmnModelInstance> models = new ArrayList<>();
    final List<String> resourceNames = new ArrayList<>();
    if (markerEnabled) {
      models.add(
          buildCheckpointProcess(
              MARKER_PROCESS_ID, intervalCycle(backupCfg.getCheckpointInterval()), "MARKER"));
      resourceNames.add("marker-checkpoint-scheduler.bpmn");
    }
    if (backupEnabled) {
      models.add(
          buildCheckpointProcess(
              BACKUP_PROCESS_ID, scheduleCycle(backupCfg.getSchedule()), "SCHEDULED_BACKUP"));
      resourceNames.add("backup-checkpoint-scheduler.bpmn");
    }
    if (retentionEnabled) {
      models.add(
          buildRetentionProcess(scheduleCycle(backupCfg.getRetention().getCleanupSchedule())));
      resourceNames.add("retention-cleanup-scheduler.bpmn");
    }
    if (models.isEmpty()) {
      return;
    }
    var cmd =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(models.get(0), resourceNames.get(0));
    for (int i = 1; i < models.size(); i++) {
      cmd = cmd.addProcessModel(models.get(i), resourceNames.get(i));
    }
    cmd.send()
        .whenComplete(
            (response, error) -> {
              if (error != null) {
                LOG.warn("Failed to deploy scheduler BPMNs", error);
              } else {
                LOG.info(
                    "Deployed {} scheduler BPMN(s) with config-derived timers",
                    response.getProcesses().size());
              }
            });
  }

  private static BpmnModelInstance buildCheckpointProcess(
      final String processId, final String timerCycle, final String checkpointType) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent("Event_Tick")
        .timerWithCycle(timerCycle)
        .serviceTask("Activity_Trigger")
        .zeebeJobType(CheckpointTriggerJobWorker.JOB_TYPE)
        .zeebeInput(checkpointType, "checkpointType")
        .endEvent("Event_End")
        .done();
  }

  private static BpmnModelInstance buildRetentionProcess(final String timerCycle) {
    return Bpmn.createExecutableProcess(RETENTION_PROCESS_ID)
        .startEvent("Event_Tick")
        .timerWithCycle(timerCycle)
        .serviceTask("Activity_Trigger")
        .zeebeJobType(RETENTION_JOB_TYPE)
        .endEvent("Event_End")
        .done();
  }

  /** ISO-8601 interval, e.g. {@code R/PT5M}. */
  private static String intervalCycle(final Duration interval) {
    return "R/" + interval.toString();
  }

  /** Translates a {@link Schedule} into a Zeebe timer cycle expression. */
  private static String scheduleCycle(final Schedule schedule) {
    return switch (schedule) {
      case final IntervalSchedule i -> intervalCycle(i.interval());
      case final CronSchedule c -> c.cronExpr().asString();
      case final NoneSchedule n ->
          throw new IllegalStateException("Cannot derive timer from NoneSchedule");
    };
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

  private boolean isRetentionEnabled() {
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

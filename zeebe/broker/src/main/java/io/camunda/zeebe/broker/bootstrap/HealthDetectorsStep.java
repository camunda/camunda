/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.system.monitoring.DetectorNodeMetrics;
import io.camunda.zeebe.broker.system.monitoring.detector.ClusterMembershipDetector;
import io.camunda.zeebe.broker.system.monitoring.detector.DiskSpaceDetector;
import io.camunda.zeebe.broker.system.monitoring.detector.HealthDetector;
import io.camunda.zeebe.broker.system.monitoring.detector.HealthDetectorFactory;
import io.camunda.zeebe.broker.system.monitoring.detector.HealthDetectorService;
import io.camunda.zeebe.broker.system.monitoring.detector.PartitionRoleRecorder;
import io.camunda.zeebe.broker.system.monitoring.detector.SampledDetector;
import io.camunda.zeebe.broker.system.monitoring.history.HealthHistory;
import io.camunda.zeebe.broker.system.monitoring.history.HealthTreeRecorder;
import io.camunda.zeebe.broker.system.monitoring.history.NodeLifecycleRecorder;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.VersionUtil;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Prototype: starts the health detectors and the health history when {@code
 * experimental.healthDetectors.enabled} is set. Runs after the health check service started and
 * before the partitions, so that it sees every partition's role changes.
 */
@NullMarked
final class HealthDetectorsStep extends AbstractBrokerStartupStep {

  static final String HISTORY_FILE = "health-history.jsonl";

  private final List<HealthDetector> detectors = new ArrayList<>();
  private @Nullable HealthHistory history;
  private @Nullable NodeLifecycleRecorder lifecycle;
  private @Nullable HealthTreeRecorder treeRecorder;
  private @Nullable PartitionRoleRecorder roles;
  private @Nullable ClusterMembershipDetector membership;
  private @Nullable DiskSpaceDetector diskSpace;
  private @Nullable HealthDetectorService service;
  private @Nullable DetectorNodeMetrics nodeMetrics;

  @Override
  public String getName() {
    return "Health Detectors";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext context,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var brokerCfg = context.getBrokerConfiguration();
    final var cfg = brokerCfg.getExperimental().getHealthDetectors();
    if (!cfg.isEnabled()) {
      startupFuture.complete(context);
      return;
    }

    final var clock = InstantSource.system();
    final var healthService = context.getHealthCheckService();
    final var file = Path.of(brokerCfg.getData().getDirectory(), HISTORY_FILE);
    history = new HealthHistory(healthService.componentName(), cfg.getHistorySize(), file, clock);

    lifecycle = new NodeLifecycleRecorder(history);
    lifecycle.recordStart(VersionUtil.getVersion());

    treeRecorder = new HealthTreeRecorder(history);
    healthService.addHealthListener(treeRecorder);

    roles = new PartitionRoleRecorder(history);
    context.addPartitionRaftListener(roles);

    if (HealthDetectorFactory.isEnabled(cfg, "clusterMembership")) {
      final var membershipService = context.getClusterServices().getMembershipService();
      membership = new ClusterMembershipDetector(membershipService, history, clock);
      membershipService.addListener(membership);
      detectors.add(membership);
    }
    if (HealthDetectorFactory.isEnabled(cfg, "diskSpace")) {
      diskSpace = new DiskSpaceDetector(clock);
      context.getDiskSpaceUsageMonitor().addDiskUsageListener(diskSpace);
      detectors.add(diskSpace);
    }

    final var sampled = HealthDetectorFactory.create(cfg, context.getMeterRegistry(), roles);
    detectors.addAll(sampled);
    detectors.forEach(healthService::registerMonitoredComponent);
    final var metrics =
        new DetectorNodeMetrics(context.getMeterRegistry(), healthService.componentName());
    detectors.forEach(detector -> detector.exposeChildren(metrics));
    nodeMetrics = metrics;

    service =
        new HealthDetectorService(
            cfg.getSampleInterval(), List.<SampledDetector>copyOf(sampled), clock);
    concurrencyControl.runOnCompletion(
        context.getActorSchedulingService().submitActor(service),
        (ok, error) -> {
          if (error != null) {
            startupFuture.completeExceptionally(error);
            return;
          }
          healthService.setHealthHistory(history);
          startupFuture.complete(context);
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext context,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    if (history == null) {
      shutdownFuture.complete(context);
      return;
    }

    final var healthService = context.getHealthCheckService();
    detectors.forEach(healthService::removeMonitoredComponent);
    detectors.clear();
    if (nodeMetrics != null) {
      nodeMetrics.close();
      nodeMetrics = null;
    }
    if (membership != null) {
      context.getClusterServices().getMembershipService().removeListener(membership);
    }
    if (diskSpace != null && context.getDiskSpaceUsageMonitor() != null) {
      context.getDiskSpaceUsageMonitor().removeDiskUsageListener(diskSpace);
    }
    if (roles != null) {
      context.removePartitionRaftListener(roles);
    }

    final var closed = service == null ? null : service.closeAsync();
    final Runnable finish =
        () -> {
          if (treeRecorder != null) {
            healthService.removeHealthListener(treeRecorder);
          }
          if (lifecycle != null) {
            lifecycle.recordStop();
          }
          healthService.setHealthHistory(null);
          if (history != null) {
            history.close();
          }
          history = null;
          shutdownFuture.complete(context);
        };
    if (closed == null) {
      finish.run();
    } else {
      concurrencyControl.runOnCompletion(closed, (ok, error) -> finish.run());
    }
  }
}

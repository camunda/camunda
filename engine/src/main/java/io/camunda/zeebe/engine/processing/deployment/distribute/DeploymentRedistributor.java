/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.distribute;

import static io.camunda.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;

import io.camunda.zeebe.engine.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.state.immutable.DeploymentState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeploymentRedistributor implements StreamProcessorLifecycleAware {

  public static final Duration DEPLOYMENT_REDISTRIBUTION_INTERVAL = Duration.ofSeconds(10);
  private static final Duration RETRY_MAX_BACKOFF_DURATION = Duration.ofMinutes(5);
  private static final long MAX_RETRY_CYCLES =
      RETRY_MAX_BACKOFF_DURATION.dividedBy(DEPLOYMENT_REDISTRIBUTION_INTERVAL);
  private static final Logger LOG = LoggerFactory.getLogger(DeploymentRedistributor.class);
  private final DeploymentDistributionCommandSender deploymentDistributionCommandSender;
  private final DeploymentState deploymentState;
  private final Map<PendingDistribution, Long> retryCyclesPerDistribution = new HashMap<>();

  public DeploymentRedistributor(
      final DeploymentDistributionCommandSender deploymentDistributionCommandSender,
      final DeploymentState deploymentState) {
    this.deploymentDistributionCommandSender = deploymentDistributionCommandSender;
    this.deploymentState = deploymentState;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    if (context.getPartitionId() != DEPLOYMENT_PARTITION) {
      return;
    }

    context
        .getScheduleService()
        .runAtFixedRate(DEPLOYMENT_REDISTRIBUTION_INTERVAL, this::runRetryCycle);
  }

  private void runRetryCycle() {
    final var pendingDistributions = new HashSet<PendingDistribution>();
    deploymentState.foreachPendingDeploymentDistribution(
        (deploymentKey, partitionId, directBuffer) -> {
          final var pending = new PendingDistribution(deploymentKey, partitionId);
          pendingDistributions.add(pending);
          retryDistribution(pending, directBuffer);
        });
    // Remove retry cycle tracking for completed distributions
    retryCyclesPerDistribution.keySet().removeIf(Predicate.not(pendingDistributions::contains));
  }

  private void retryDistribution(
      final PendingDistribution pending, final DirectBuffer copiedDeploymentBuffer) {
    if (!shouldRetryNow(pending)) {
      return;
    }

    LOG.info(
        "Retrying to distribute deployment {} to partition {}",
        pending.deploymentKey,
        pending.partitionId);
    final var deploymentRecord = new DeploymentRecord();
    deploymentRecord.wrap(copiedDeploymentBuffer);
    deploymentDistributionCommandSender.distributeToPartition(
        pending.deploymentKey, pending.partitionId, deploymentRecord);
  }

  private boolean shouldRetryNow(final PendingDistribution pendingDistribution) {
    // retryCycles starts off at 0, ensuring that we wait between DEPLOYMENT_REDISTRIBUTION_INTERVAL
    // and 2 * DEPLOYMENT_REDISTRIBUTION_INTERVAL before retrying distribution.
    final long retryCycle =
        retryCyclesPerDistribution.compute(
            pendingDistribution, (k, retryCycles) -> retryCycles != null ? retryCycles + 1 : 0L);

    if (retryCycle >= MAX_RETRY_CYCLES) {
      // Retry in intervals of RETRY_MAX_BACKOFF_DURATION
      return retryCycle % MAX_RETRY_CYCLES == 0;
    } else {
      // Retry in intervals of DEPLOYMENT_REDISTRIBUTION_INTERVAL
      // The interval is doubling until we reached RETRY_MAX_BACKOFF_DURATION
      return Long.bitCount(retryCycle) == 1;
    }
  }

  private record PendingDistribution(long deploymentKey, int partitionId) {}
}

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
  private static final long RETRY_MAX_BACKOFF_ATTEMPTS =
      RETRY_MAX_BACKOFF_DURATION.dividedBy(DEPLOYMENT_REDISTRIBUTION_INTERVAL);
  private static final Logger LOG = LoggerFactory.getLogger(DeploymentRedistributor.class);
  private final int partitionsCount;
  private final DeploymentDistributionCommandSender deploymentDistributionCommandSender;
  private final DeploymentState deploymentState;
  private final Map<PendingDistribution, Long> distributionAttempts = new HashMap<>();
  private DeploymentDistributionBehavior deploymentDistributionBehavior;

  public DeploymentRedistributor(
      final int partitionsCount,
      final DeploymentDistributionCommandSender deploymentDistributionCommandSender,
      final DeploymentState deploymentState) {
    this.partitionsCount = partitionsCount;
    this.deploymentDistributionCommandSender = deploymentDistributionCommandSender;
    this.deploymentState = deploymentState;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    if (context.getPartitionId() != DEPLOYMENT_PARTITION) {
      return;
    }
    deploymentDistributionBehavior =
        new DeploymentDistributionBehavior(
            context.getWriters(), partitionsCount, deploymentDistributionCommandSender);

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
    // Remove attempts for completed distributions
    distributionAttempts.keySet().removeIf(Predicate.not(pendingDistributions::contains));
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
    deploymentDistributionBehavior.distributeDeploymentToPartition(
        pending.deploymentKey, pending.partitionId, copiedDeploymentBuffer);
  }

  private boolean shouldRetryNow(final PendingDistribution pendingDistribution) {
    // attempt starts off at 0, ensuring that we wait between DEPLOYMENT_REDISTRIBUTION_INTERVAL and
    // 2 * DEPLOYMENT_REDISTRIBUTION_INTERVAL before retrying distribution.
    final long attempt =
        distributionAttempts.compute(
            pendingDistribution, (k, attempts) -> attempts != null ? attempts + 1 : 0L);

    if (attempt >= RETRY_MAX_BACKOFF_ATTEMPTS) {
      // Retry in intervals of RETRY_MAX_BACKOFF_DURATION
      return attempt % RETRY_MAX_BACKOFF_ATTEMPTS == 0;
    } else {
      // Retry in intervals of DEPLOYMENT_REDISTRIBUTION_INTERVAL
      // The interval is doubling until we reached RETRY_MAX_BACKOFF_DURATION
      return Long.bitCount(attempt) == 1;
    }
  }

  private record PendingDistribution(long deploymentKey, int partitionId) {}
}

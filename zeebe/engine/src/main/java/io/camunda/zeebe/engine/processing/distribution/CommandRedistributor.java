/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.distribution;

import io.camunda.zeebe.engine.state.immutable.DistributionState;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Command Redistributor provides a mechanism to retry sending {@link
 * CommandDistributionRecord}s to other partitions. This is needed because the communication between
 * partitions is unreliable.
 *
 * <p>A simple exponential backoff is used for retrying these retriable distributions. This
 * exponential backoff is configurable through {@link
 * io.camunda.zeebe.engine.EngineConfiguration#getCommandRedistributionInterval()} and {@link
 * io.camunda.zeebe.engine.EngineConfiguration#getCommandRedistributionMaxBackoff()}, with defaults
 * of 10 seconds and 5 minutes respectively. The backoff starts at the initial interval and doubles
 * every retry until it reaches the maximum backoff duration. This backoff is tracked for each
 * retriable distribution individually.
 */
public final class CommandRedistributor implements StreamProcessorLifecycleAware {

  /**
   * Specifies how often this redistributor runs, i.e. the fixed delay between runs. It is also used
   * to specify the initial interval for retrying a specific retriable distribution.
   */
  private final Duration commandRedistributionInterval;

  /**
   * Specifies the maximum backoff interval for retrying a specific retriable distribution, i.e. the
   * maximum delay between two retries of the same retriable distribution.
   */
  private final Duration retryMaxBackoffDuration;

  /**
   * This calculated value specifies the maximum number of retry cycles until the {@link
   * #retryMaxBackoffDuration} is reached by the exponential backoff.
   */
  private final long maxRetryCycles;

  private static final Logger LOG = LoggerFactory.getLogger(CommandRedistributor.class);

  private final DistributionState distributionState;
  private final InterPartitionCommandSender commandSender;

  private boolean isDistributionPaused = false;

  /**
   * Tracks the number of attempted retry cycles for each retriable distribution. Note that this
   * includes retry cycles where the retriable distribution was not resend due to exponential
   * backoff.
   */
  private final Map<RetriableDistribution, Long> retryCyclesPerDistribution = new HashMap<>();

  public CommandRedistributor(
<<<<<<< HEAD
      final DistributionState distributionState,
      final InterPartitionCommandSender commandSender,
      final boolean isDistributionPaused) {
    this.distributionState = distributionState;
    this.commandSender = commandSender;
=======
      final CommandDistributionBehavior distributionBehavior,
      final RoutingInfo routingInfo,
      final boolean isDistributionPaused,
      final Duration commandRedistributionInterval,
      final Duration retryMaxBackoffDuration) {
    this.distributionBehavior = distributionBehavior;
    this.routingInfo = routingInfo;
>>>>>>> 25ce09a8 (feat: Implement configurable command distribution retry intervals)
    this.isDistributionPaused = isDistributionPaused;
    this.commandRedistributionInterval = commandRedistributionInterval;
    this.retryMaxBackoffDuration = retryMaxBackoffDuration;
    this.maxRetryCycles = retryMaxBackoffDuration.dividedBy(commandRedistributionInterval);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    if (isDistributionPaused) {
      LOG.debug("Command distribution is paused, skipping retry scheduling.");
      return;
    }
    context
        .getScheduleService()
        .runAtFixedRate(commandRedistributionInterval, this::runRetryCycle);
  }

  public void runRetryCycle() {
    final var retriableDistributions = new HashSet<RetriableDistribution>();
    distributionState.foreachRetriableDistribution(
        (distributionKey, record) -> {
<<<<<<< HEAD
          final var retriable = new RetriableDistribution(distributionKey, record.getPartitionId());
=======
          // If the partition is currently being scaled up, we won't yet try distributing to it.
          if (routingInfo.isPartitionScaling(record.getPartitionId())) {
            LOG.debug(
                "Excluding distribution {} for partition {} as it is currently scaling up.",
                distributionKey,
                record.getPartitionId());
            return true;
          }

          final var retriable = RetriableDistribution.from(distributionKey, record);
          final Long retryCycle = updateRetryCycle(retriable);

          if (retriable.shouldRetryNow(retryCycle, maxRetryCycles)) {
            retryDistribution(retriable, record, retryCycle);
          }

>>>>>>> 25ce09a8 (feat: Implement configurable command distribution retry intervals)
          retriableDistributions.add(retriable);
          retryDistribution(retriable, record);
        });

    // Remove retry cycle tracking for completed distributions, i.e. those not visited in this cycle
    retryCyclesPerDistribution.keySet().removeIf(Predicate.not(retriableDistributions::contains));
  }

  private void retryDistribution(
      final RetriableDistribution retriable,
      final CommandDistributionRecord commandDistributionRecord) {
    if (!shouldRetryNow(retriable)) {
      return;
    }

    LOG.info(
        "Retrying to distribute retriable command {} to partition {}",
        retriable.distributionKey,
        retriable.partitionId);

    commandSender.sendCommand(
        retriable.partitionId,
        commandDistributionRecord.getValueType(),
        commandDistributionRecord.getIntent(),
        retriable.distributionKey,
        commandDistributionRecord.getCommandValue());
  }

<<<<<<< HEAD
  /**
   * Returns whether a retriable distribution should be retried now, or not in this cycle.
   *
   * <p>Calling this method increments the retry cycles tracking ({@link
   * #retryCyclesPerDistribution}) of that retriable distribution, or initiates it at 0. The number
   * of cycles is used to implement a simple exponential backoff.
   */
  private boolean shouldRetryNow(final RetriableDistribution retriableDistribution) {
    // retryCycles starts off at 0, ensuring that we wait between COMMAND_REDISTRIBUTION_INTERVAL
    // and 2 * COMMAND_REDISTRIBUTION_INTERVAL before retrying distribution.
    final long retryCycle =
        retryCyclesPerDistribution.compute(
            retriableDistribution, (k, retryCycles) -> retryCycles != null ? retryCycles + 1 : 0L);

    if (retryCycle >= MAX_RETRY_CYCLES) {
      // Retry in intervals of RETRY_MAX_BACKOFF_DURATION
      return retryCycle % MAX_RETRY_CYCLES == 0;
    } else {
      // Retry in intervals of COMMAND_REDISTRIBUTION_INTERVAL
      // The interval is doubling until we reached RETRY_MAX_BACKOFF_DURATION
      return Long.bitCount(retryCycle) == 1;
=======
  private record RetriableDistribution(long distributionKey, int partitionId) {
    /**
     * Returns whether a retriable distribution should be retried now, or not in the given cycle.
     *
     * <p>The number of cycles is used to implement a simple exponential backoff.
     */
    private boolean shouldRetryNow(final Long retryCycle, final long maxRetryCycles) {
      // retryCycles starts off at 0, ensuring that we wait between commandRedistributionInterval
      // and 2 * commandRedistributionInterval before retrying distribution.

      if (retryCycle >= maxRetryCycles) {
        // Retry in intervals of retryMaxBackoffDuration
        return retryCycle % maxRetryCycles == 0;
      } else {
        // Retry in intervals of commandRedistributionInterval
        // The interval is doubling until we reached retryMaxBackoffDuration
        return Long.bitCount(retryCycle) == 1;
      }
    }

    public static RetriableDistribution from(
        final long distributionKey, final CommandDistributionRecord record) {
      return new RetriableDistribution(distributionKey, record.getPartitionId());
>>>>>>> 25ce09a8 (feat: Implement configurable command distribution retry intervals)
    }
  }

  private record RetriableDistribution(long distributionKey, int partitionId) {}
}

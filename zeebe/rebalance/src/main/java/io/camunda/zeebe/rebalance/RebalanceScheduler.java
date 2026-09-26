/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationUpdateNotifier.ClusterConfigurationUpdateListener;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.rebalance.ClusterLeadershipStatus.State;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.ConfigurationChangeInProgressException;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.NotCoordinatorException;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.RebalanceInProgressException;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.util.schedule.Schedule;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts a rebalance each time the schedule is due, unless the cluster is already balanced or
 * busier than any configured threshold, in which case it waits for the next time. The load is
 * measured over the window leading up to each due time, and whether the cluster is balanced is
 * checked once it is due.
 *
 * <p>Runs on every member, but only the coordinator gets past the first status check; the others
 * skip quietly.
 */
public final class RebalanceScheduler implements ClusterConfigurationUpdateListener, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(RebalanceScheduler.class);

  private final ConcurrencyControl executor;
  private final Schedule schedule;
  private final RebalanceApi coordinator;
  private final ClusterLoadSource loadSource;
  private final Map<LoadMeasure, Double> maxRatesPerSecond;
  private final Duration loadWindow;
  private final InstantSource clock;
  private final ClusterRebalanceMetrics metrics;

  private Set<MemberId> members = Set.of();
  private @Nullable ScheduledTimer timer;
  private @Nullable Instant lastDue;
  private boolean closed;

  /**
   * @param maxRatesPerSecond the cluster-wide rate of each measure above which a due rebalance is
   *     skipped; measures without an entry are not limited
   * @param loadWindow the window the load is averaged over
   */
  public RebalanceScheduler(
      final ConcurrencyControl executor,
      final Schedule schedule,
      final RebalanceApi coordinator,
      final ClusterLoadSource loadSource,
      final Map<LoadMeasure, Double> maxRatesPerSecond,
      final Duration loadWindow,
      final InstantSource clock,
      final ClusterRebalanceMetrics metrics) {
    this.executor = executor;
    this.schedule = schedule;
    this.coordinator = coordinator;
    this.loadSource = loadSource;
    this.maxRatesPerSecond = Map.copyOf(maxRatesPerSecond);
    this.loadWindow = loadWindow;
    this.clock = clock;
    this.metrics = metrics;
  }

  public void start() {
    executor.run(this::scheduleNext);
  }

  @Override
  public void close() {
    executor.run(
        () -> {
          closed = true;
          if (timer != null) {
            timer.cancel();
            timer = null;
          }
        });
  }

  @Override
  public void onClusterConfigurationUpdated(final ClusterConfiguration clusterConfiguration) {
    onClusterConfigurationUpdated(CurrentClusterConfiguration.fromLegacy(clusterConfiguration));
  }

  @Override
  public void onClusterConfigurationUpdated(
      final CurrentClusterConfiguration clusterConfiguration) {
    executor.run(
        () -> {
          if (!clusterConfiguration.isUninitialized()) {
            members = clusterConfiguration.liveMembers();
          }
        });
  }

  /**
   * Schedules the next due rebalance, starting early enough to measure the load over the whole
   * window before it is due. A due time too soon to measure for is skipped. Each due time is
   * scheduled as soon as the previous one starts, so their windows may overlap.
   */
  private void scheduleNext() {
    if (closed) {
      return;
    }
    final var now = clock.instant();
    final var lead = maxRatesPerSecond.isEmpty() ? Duration.ZERO : loadWindow;
    var due = schedule.nextExecution(lastDue != null ? lastDue : now);
    while (due.isPresent() && due.get().minus(lead).isBefore(now)) {
      final var next = schedule.nextExecution(due.get());
      if (next.isPresent() && !next.get().isAfter(due.get())) {
        break;
      }
      due = next;
    }
    if (due.isEmpty() || due.get().minus(lead).isBefore(now)) {
      LOG.warn("Schedule {} has no further executions", schedule);
      return;
    }
    final var nextDue = due.get();
    LOG.debug("Next scheduled rebalance is due at {}", nextDue);
    timer = executor.schedule(Duration.between(now, nextDue.minus(lead)), () -> runDue(nextDue));
  }

  private void runDue(final Instant due) {
    timer = null;
    lastDue = due;
    scheduleNext();
    if (maxRatesPerSecond.isEmpty()) {
      triggerIfDue(null);
      return;
    }
    executor.runOnCompletion(
        coordinator.getRebalanceStatus(),
        (ignored, error) -> {
          if (error != null) {
            finish(outcomeOf(error), error);
          } else if (members.isEmpty()) {
            finish(ScheduledRebalanceOutcome.LOAD_UNKNOWN, null);
          } else {
            final var measured = members;
            executor.runOnCompletion(
                loadSource.collect(measured, loadWindow),
                (load, loadError) -> {
                  if (loadError != null) {
                    finish(ScheduledRebalanceOutcome.FAILED, loadError);
                  } else if (!members.equals(measured)) {
                    LOG.info(
                        "Skipping scheduled rebalance, members changed from {} to {} over the last {}",
                        measured,
                        members,
                        loadWindow);
                    finish(ScheduledRebalanceOutcome.LOAD_UNKNOWN, null);
                  } else {
                    metrics.observeScheduledLoad(load);
                    triggerIfDue(load);
                  }
                });
          }
        });
  }

  /**
   * @param load the load measured over the window leading up to now, or {@code null} if no
   *     threshold is configured
   */
  private void triggerIfDue(final @Nullable ClusterLoad load) {
    executor.runOnCompletion(
        coordinator.getRebalanceStatus(),
        (status, error) -> {
          if (error != null) {
            finish(outcomeOf(error), error);
          } else if (status.running() != null) {
            finish(ScheduledRebalanceOutcome.ALREADY_RUNNING, null);
          } else if (status.leadershipStatus().state() == State.BALANCED) {
            finish(ScheduledRebalanceOutcome.ALREADY_BALANCED, null);
          } else if (load == null) {
            trigger();
          } else {
            triggerIfQuiet(load);
          }
        });
  }

  private void triggerIfQuiet(final ClusterLoad load) {
    if (!load.isComplete()) {
      LOG.info(
          "Skipping scheduled rebalance, no load report over the last {} from {}",
          loadWindow,
          load.unaccounted());
      finish(ScheduledRebalanceOutcome.LOAD_UNKNOWN, null);
      return;
    }
    for (final var limit : maxRatesPerSecond.entrySet()) {
      final double rate = load.ratesPerSecond().getOrDefault(limit.getKey(), 0.0);
      if (rate > limit.getValue()) {
        LOG.info(
            "Skipping scheduled rebalance, {} at {}/s over the last {} exceeds {}/s",
            limit.getKey(),
            rate,
            loadWindow,
            limit.getValue());
        finish(ScheduledRebalanceOutcome.BUSY, null);
        return;
      }
    }
    trigger();
  }

  private void trigger() {
    if (closed) {
      return;
    }
    executor.runOnCompletion(
        coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings()),
        (status, error) -> {
          if (error != null) {
            finish(outcomeOf(error), error);
          } else {
            LOG.info("Started scheduled rebalance");
            finish(ScheduledRebalanceOutcome.STARTED, null);
          }
        });
  }

  private void finish(
      final @Nullable ScheduledRebalanceOutcome outcome, final @Nullable Throwable error) {
    if (outcome == ScheduledRebalanceOutcome.FAILED) {
      LOG.warn("Scheduled rebalance failed", error);
    } else if (outcome != null) {
      LOG.debug("Scheduled rebalance was due: {}", outcome);
    }
    if (outcome != null) {
      metrics.observeScheduledRun(outcome);
    }
  }

  /** The outcome of a coordinator request that failed, or {@code null} if we don't coordinate. */
  private static @Nullable ScheduledRebalanceOutcome outcomeOf(final Throwable error) {
    final var cause =
        error instanceof RebalanceRequestFailedException || error.getCause() == null
            ? error
            : error.getCause();
    return switch (cause) {
      case final NotCoordinatorException ignored -> null;
      case final RebalanceInProgressException ignored -> ScheduledRebalanceOutcome.ALREADY_RUNNING;
      case final ConfigurationChangeInProgressException ignored ->
          ScheduledRebalanceOutcome.CONFIGURATION_CHANGE_IN_PROGRESS;
      default -> ScheduledRebalanceOutcome.FAILED;
    };
  }
}

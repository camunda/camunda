/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationUpdateNotifier.ClusterConfigurationUpdateListener;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.metrics.ClusterRebalanceMetrics;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceRequestFailedException.NotCoordinator;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceRequestFailedException.RebalanceInProgress;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.function.LongSupplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sequences leadership transfers across the cluster on behalf of an operator, so that leadership
 * returns to each partition's desired leader one partition at a time.
 *
 * <p>Every member runs one of these, but only the lowest-id member of the cluster configuration
 * acts as the coordinator; the others refuse the requests forwarded to them, which is how a
 * forwarder working from a stale view of the configuration finds out its view is stale. A member
 * that loses the position drops what it was doing rather than handing it on: the state is in memory
 * only, so a rebalance does not survive a coordinator restart or a change of coordinator, and the
 * operator triggers again. Nothing is lost by that - a transfer already handed to a partition's
 * leader runs to its own conclusion there, and every partition not yet reached simply keeps its
 * current leader.
 */
@NullMarked
public final class RebalanceCoordinator
    implements RebalanceApi, ClusterConfigurationUpdateListener {

  private static final Logger LOG = LoggerFactory.getLogger(RebalanceCoordinator.class);

  private final MemberId localMemberId;
  private final ConcurrencyControl executor;
  private final RebalanceRunner runner;
  private final LongSupplier rebalanceIdGenerator;
  private final ClusterRebalanceMetrics metrics;

  /**
   * The configuration this member coordinates under, or {@code null} while it does not coordinate.
   */
  private @Nullable ClusterConfiguration configuration;

  private @Nullable RebalanceRun running;
  private RebalanceStatus.@Nullable Completed lastCompleted;

  public RebalanceCoordinator(
      final MemberId localMemberId,
      final ConcurrencyControl executor,
      final RebalanceRunner runner,
      final LongSupplier rebalanceIdGenerator,
      final ClusterRebalanceMetrics metrics) {
    this.localMemberId = localMemberId;
    this.executor = executor;
    this.runner = runner;
    this.rebalanceIdGenerator = rebalanceIdGenerator;
    this.metrics = metrics;
  }

  @Override
  public void onClusterConfigurationUpdated(final ClusterConfiguration clusterConfiguration) {
    executor.run(() -> updateCoordinatorRole(clusterConfiguration));
  }

  @Override
  public ActorFuture<RebalanceStatus> triggerRebalance(final TriggerRebalanceRequest request) {
    final ActorFuture<RebalanceStatus> result = executor.createFuture();
    executor.run(
        () -> {
          final var coordinatedConfiguration = coordinatedConfiguration(result);
          if (coordinatedConfiguration == null) {
            return;
          }
          final var inFlight = running;
          if (inFlight != null) {
            result.completeExceptionally(
                new RebalanceInProgress(
                    "Rebalance %d is already running".formatted(inFlight.id())));
            return;
          }
          final var rebalance =
              new RebalanceRun(
                  rebalanceIdGenerator.getAsLong(),
                  request.overrides(),
                  request.dryRun(),
                  coordinatedConfiguration);
          running = rebalance;
          LOG.info("Starting {}rebalance {}", rebalance.dryRun() ? "dry-run " : "", rebalance.id());
          if (rebalance.dryRun()) {
            // A dry run only reads the plan, so it is over within the request and answers with the
            // plan itself rather than with a rebalance the operator would have to come back for.
            start(rebalance, () -> result.complete(status()));
          } else {
            // Answer before running so the operator sees the rebalance they started, whatever it
            // goes on to do.
            result.complete(status());
            start(rebalance, () -> {});
          }
        });
    return result;
  }

  @Override
  public ActorFuture<RebalanceStatus> getRebalanceStatus() {
    final ActorFuture<RebalanceStatus> result = executor.createFuture();
    executor.run(
        () -> {
          if (coordinatedConfiguration(result) == null) {
            return;
          }
          result.complete(status());
        });
    return result;
  }

  @Override
  public ActorFuture<CancelRebalanceResponse> cancelRebalance() {
    final ActorFuture<CancelRebalanceResponse> result = executor.createFuture();
    executor.run(
        () -> {
          if (coordinatedConfiguration(result) == null) {
            return;
          }
          final var inFlight = running;
          if (inFlight == null) {
            result.complete(new CancelRebalanceResponse(false));
            return;
          }
          inFlight.requestCancel();
          LOG.info(
              "Cancelling rebalance {}, which stops once the transfer in flight finishes",
              inFlight.id());
          result.complete(new CancelRebalanceResponse(true));
        });
    return result;
  }

  private void start(final RebalanceRun rebalance, final Runnable whenFinished) {
    final ActorFuture<Void> run;
    try {
      run = runner.run(rebalance);
    } catch (final Exception e) {
      finish(rebalance, e);
      whenFinished.run();
      return;
    }
    executor.runOnCompletion(
        run,
        (ignored, error) -> {
          finish(rebalance, error);
          whenFinished.run();
        });
  }

  private void finish(final RebalanceRun rebalance, final @Nullable Throwable error) {
    if (running != rebalance) {
      // We stopped coordinating while the rebalance was in flight, so its state is already gone and
      // resurrecting it here would report a rebalance this member no longer owns.
      return;
    }
    running = null;
    final RebalanceOutcome outcome;
    if (error != null) {
      LOG.warn("Rebalance {} failed", rebalance.id(), error);
      outcome = RebalanceOutcome.FAILED;
    } else if (rebalance.isCancelRequested()) {
      outcome = RebalanceOutcome.CANCELLED;
    } else {
      outcome = RebalanceOutcome.COMPLETED;
    }
    lastCompleted =
        new RebalanceStatus.Completed(
            rebalance.id(), outcome, rebalance.dryRun(), rebalance.partitions());
    metrics.observeElapsed(outcome, rebalance.elapsed());
    // The gauges describe a rebalance in flight and there is no longer one, so they come down here
    // rather than being left holding their last values until the next rebalance overwrites them.
    metrics.clear();
    LOG.info("Rebalance {} finished as {} after {}", rebalance.id(), outcome, rebalance.elapsed());
  }

  private void updateCoordinatorRole(final ClusterConfiguration clusterConfiguration) {
    if (clusterConfiguration.isUninitialized()) {
      // Nobody coordinates until there is a configuration to pick the lowest-id member of.
      return;
    }
    final var coordinator =
        ClusterConfigurationCoordinatorSupplier.ofMembers(clusterConfiguration.members().keySet())
            .getDefaultCoordinator();
    final var nowCoordinating = localMemberId.equals(coordinator);
    if (nowCoordinating != (configuration != null)) {
      if (nowCoordinating) {
        LOG.info("Coordinating rebalances as the lowest-id member of the cluster configuration");
        metrics.startCoordinating();
      } else {
        LOG.info("No longer coordinating rebalances, {} is now the lowest-id member", coordinator);
        discardState();
      }
    }
    // Kept up to date so that the next rebalance is admitted under, and pins, the configuration
    // this member coordinates under now rather than the one it took the role with.
    configuration = nowCoordinating ? clusterConfiguration : null;
  }

  private void discardState() {
    final var inFlight = running;
    if (inFlight != null) {
      inFlight.abandon();
      LOG.warn(
          "Abandoning rebalance {}; partitions already transferred keep their new leaders, the rest "
              + "keep their current ones, and the rebalance has to be triggered again",
          inFlight.id());
    }
    running = null;
    lastCompleted = null;
    metrics.clear();
  }

  /**
   * The configuration this member coordinates under, or {@code null} - having refused {@code
   * result} - if it is not the coordinator.
   */
  private @Nullable ClusterConfiguration coordinatedConfiguration(final ActorFuture<?> result) {
    final var current = configuration;
    if (current == null) {
      result.completeExceptionally(
          new NotCoordinator(
              "Member %s is not the rebalancing coordinator".formatted(localMemberId.id())));
    }
    return current;
  }

  private RebalanceStatus status() {
    final var inFlight = running;
    final var runningStatus =
        inFlight == null
            ? null
            : new RebalanceStatus.Running(
                inFlight.id(),
                inFlight.overrides(),
                inFlight.dryRun(),
                inFlight.isCancelRequested(),
                inFlight.partitions());
    return new RebalanceStatus(runningStatus, lastCompleted);
  }
}

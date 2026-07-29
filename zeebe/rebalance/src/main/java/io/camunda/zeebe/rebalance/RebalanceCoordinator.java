/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import static java.util.Objects.requireNonNull;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationUpdateNotifier.ClusterConfigurationUpdateListener;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.NotCoordinatorException;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.RebalanceInProgressException;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.function.LongSupplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sequences leadership transfers across the cluster so that leadership moves to each partition's
 * desired leader one partition at a time.
 *
 * <p>The coordinator is present on each member, but only the lowest-id member of the cluster
 * configuration accepts requests; the others refuse the requests forwarded to them. Rebalance state
 * is in memory only, so a running rebalance does not survive a coordinator restart or a change of
 * coordinator. A member that stops being eligible to act as the coordinator drops any currently
 * running rebalance.
 */
@NullMarked
public final class RebalanceCoordinator
    implements RebalanceApi, ClusterConfigurationUpdateListener {

  private static final Logger LOG = LoggerFactory.getLogger(RebalanceCoordinator.class);

  private final MemberId localMemberId;
  private final ConcurrencyControl executor;
  private final RebalanceRunner runner;
  private final LongSupplier rebalanceIdGenerator;

  private boolean coordinating;
  private @Nullable RebalanceRun running;

  /**
   * The in-flight run's own completion future, kept alongside {@link #running} so a race between a
   * cancellation request and the runner's completion can be resolved by checking whether the run
   * has already finished, rather than by inferring it from {@link RebalanceRun#isCancelRequested()}
   * having been set before {@link #finish} happened to be scheduled.
   */
  private @Nullable ActorFuture<Void> runningRun;

  private RebalanceStatus.@Nullable Completed lastCompleted;

  public RebalanceCoordinator(
      final MemberId localMemberId,
      final ConcurrencyControl executor,
      final RebalanceRunner runner,
      final LongSupplier rebalanceIdGenerator) {
    this.localMemberId = localMemberId;
    this.executor = executor;
    this.runner = runner;
    this.rebalanceIdGenerator = rebalanceIdGenerator;
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
          if (rejectIfNotCoordinating(result)) {
            return;
          }
          final var inFlight = running;
          if (inFlight != null) {
            result.completeExceptionally(
                new RebalanceInProgressException(
                    "Rebalance %d is already running".formatted(inFlight.id())));
            return;
          }
          final var rebalance =
              new RebalanceRun(
                  rebalanceIdGenerator.getAsLong(), request.overrides(), request.dryRun());
          running = rebalance;
          LOG.info("Starting {}rebalance {}", rebalance.dryRun() ? "dry-run " : "", rebalance.id());
          // Answer before running so the operator sees the rebalance they started, whatever it goes
          // on to do.
          result.complete(status());
          start(rebalance);
        });
    return result;
  }

  @Override
  public ActorFuture<RebalanceStatus> getRebalanceStatus() {
    final ActorFuture<RebalanceStatus> result = executor.createFuture();
    executor.run(
        () -> {
          if (rejectIfNotCoordinating(result)) {
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
          if (rejectIfNotCoordinating(result)) {
            return;
          }
          final var inFlight = running;
          if (inFlight == null || requireNonNull(runningRun).isDone()) {
            // Either nothing is running, or it already finished and just hasn't been reported by
            // finish() yet: either way, there is nothing left to cancel.
            result.complete(new CancelRebalanceResponse(false));
            return;
          }
          inFlight.requestCancel();
          LOG.info(
              "Cancelling rebalance {} (will stop once any transfer in flight finishes)",
              inFlight.id());
          result.complete(new CancelRebalanceResponse(true));
        });
    return result;
  }

  private void start(final RebalanceRun rebalance) {
    final ActorFuture<Void> run;
    try {
      run = runner.run(rebalance);
    } catch (final Exception e) {
      finish(rebalance, e);
      return;
    }
    runningRun = run;
    executor.runOnCompletion(run, (ignored, error) -> finish(rebalance, error));
  }

  private void finish(final RebalanceRun rebalance, final @Nullable Throwable error) {
    if (running != rebalance) {
      // We stopped coordinating while the rebalance was in flight, so its state is already gone
      return;
    }
    running = null;
    runningRun = null;
    final RebalanceOutcome outcome;
    if (error != null) {
      LOG.warn("Rebalance {} failed", rebalance.id(), error);
      outcome = RebalanceOutcome.FAILED;
    } else if (rebalance.isCancelRequested()) {
      outcome = RebalanceOutcome.CANCELLED;
    } else {
      outcome = RebalanceOutcome.COMPLETED;
    }
    lastCompleted = new RebalanceStatus.Completed(rebalance.id(), outcome);
    LOG.info("Rebalance {} finished as {}", rebalance.id(), outcome);
  }

  private void updateCoordinatorRole(final ClusterConfiguration clusterConfiguration) {
    if (clusterConfiguration.isUninitialized()) {
      return;
    }
    final var coordinator =
        ClusterConfigurationCoordinatorSupplier.ofMembers(clusterConfiguration.members().keySet())
            .getDefaultCoordinator();
    final var nowCoordinating = localMemberId.equals(coordinator);
    if (nowCoordinating == coordinating) {
      return;
    }
    coordinating = nowCoordinating;
    if (nowCoordinating) {
      LOG.info("Coordinating rebalances as the lowest-id member of the cluster configuration");
    } else {
      LOG.info("No longer coordinating rebalances, {} is now the lowest-id member", coordinator);
      discardState();
    }
  }

  private void discardState() {
    final var inFlight = running;
    if (inFlight != null) {
      inFlight.abandon();
      LOG.warn("Abandoning rebalance {}; partitions already transferred", inFlight.id());
    }
    running = null;
    runningRun = null;
    lastCompleted = null;
  }

  private boolean rejectIfNotCoordinating(final ActorFuture<?> result) {
    if (coordinating) {
      return false;
    }
    result.completeExceptionally(
        new NotCoordinatorException(
            "Member %s is not the rebalancing coordinator".formatted(localMemberId.id())));
    return true;
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
                inFlight.isCancelRequested());
    return new RebalanceStatus(runningStatus, lastCompleted);
  }
}

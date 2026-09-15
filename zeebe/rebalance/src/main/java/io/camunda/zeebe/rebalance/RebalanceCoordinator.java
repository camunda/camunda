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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.ConfigurationChangeInProgressException;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.NotCoordinatorException;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.RebalanceInProgressException;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Nulls;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
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
public final class RebalanceCoordinator
    implements RebalanceApi, ClusterConfigurationUpdateListener {

  private static final Logger LOG = LoggerFactory.getLogger(RebalanceCoordinator.class);

  private static final Set<PartitionRebalanceOutcome> SUCCESSFUL_PARTITION_OUTCOMES =
      EnumSet.of(
          PartitionRebalanceOutcome.TRANSFERRED,
          PartitionRebalanceOutcome.ALREADY_LEADER,
          PartitionRebalanceOutcome.CANCELLED,
          PartitionRebalanceOutcome.PHYSICAL_TENANT_DISABLED,
          PartitionRebalanceOutcome.PHYSICAL_TENANT_RECOVERING);

  private final MemberId localMemberId;
  private final ConcurrencyControl executor;
  private final RebalanceRunner runner;
  private final PartitionBalancePlanner balancePlanner;
  private final LongSupplier rebalanceIdGenerator;
  private final Clock clock;
  private final ClusterRebalanceMetrics metrics;

  /**
   * The configuration this member coordinates under, or {@code null} while it does not coordinate.
   */
  private @Nullable CurrentClusterConfiguration configuration;

  private @Nullable RebalanceRun running;

  private RebalanceStatus.@Nullable Completed lastCompleted;

  public RebalanceCoordinator(
      final MemberId localMemberId,
      final ConcurrencyControl executor,
      final RebalanceRunner runner,
      final PartitionBalancePlanner balancePlanner,
      final LongSupplier rebalanceIdGenerator,
      final Clock clock,
      final ClusterRebalanceMetrics metrics) {
    this.localMemberId = localMemberId;
    this.executor = executor;
    this.runner = runner;
    this.balancePlanner = balancePlanner;
    this.rebalanceIdGenerator = rebalanceIdGenerator;
    this.clock = clock;
    this.metrics = metrics;
  }

  @Override
  public void onClusterConfigurationUpdated(final ClusterConfiguration clusterConfiguration) {
    onClusterConfigurationUpdated(CurrentClusterConfiguration.fromLegacy(clusterConfiguration));
  }

  @Override
  public void onClusterConfigurationUpdated(
      final CurrentClusterConfiguration clusterConfiguration) {
    executor.run(() -> updateCoordinatorRole(clusterConfiguration));
  }

  /**
   * Shutdown is a loss of coordinator ownership, not an operator cancellation, so it reuses {@link
   * #discardState} rather than reporting the in-flight run as {@link RebalanceOutcome#CANCELLED}.
   */
  public ActorFuture<Void> shutdown() {
    final ActorFuture<Void> result = executor.createFuture();
    executor.run(
        () -> {
          configuration = null;
          discardState();
          result.complete(Nulls.uncheckedCastToNonNull(null));
        });
    return result;
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
          if (!coordinatedConfiguration.phasedChangeState().pending().isEmpty()) {
            result.completeExceptionally(
                new ConfigurationChangeInProgressException(
                    "Cannot start a rebalance while a cluster configuration change is in "
                        + "progress"));
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
                  rebalanceIdGenerator.getAsLong(),
                  request.overrides(),
                  request.dryRun(),
                  coordinatedConfiguration,
                  clock.instant());
          running = rebalance;
          LOG.info("Starting {}rebalance {}", rebalance.dryRun() ? "dry-run " : "", rebalance.id());
          if (rebalance.dryRun()) {
            // A dry run only reads the plan, so it is over within the request and answers with the
            // plan itself as a completed snapshot under `running`; it is never retained as
            // `lastCompleted`.
            start(
                rebalance,
                (completed, error) -> {
                  if (error != null) {
                    result.completeExceptionally(error);
                  } else {
                    result.complete(
                        new RebalanceStatus(
                            runningSnapshot(rebalance), lastCompleted, leadershipStatus(running)));
                  }
                });
          } else {
            result.complete(status());
            start(rebalance, (completed, error) -> {});
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
              "Cancelling rebalance {} (will stop once any transfer in flight finishes)",
              inFlight.id());
          result.complete(new CancelRebalanceResponse(true));
        });
    return result;
  }

  private void start(
      final RebalanceRun rebalance,
      final BiConsumer<RebalanceStatus.Completed, @Nullable Throwable> whenFinished) {
    final ActorFuture<Void> run;
    try {
      run = runner.run(rebalance);
    } catch (final Exception e) {
      final var completed = finish(rebalance, e);
      if (completed != null) {
        whenFinished.accept(completed, e);
      }
      return;
    }
    executor.runOnCompletion(
        run,
        (ignored, error) -> {
          final var completed = finish(rebalance, error);
          if (completed != null) {
            whenFinished.accept(completed, error);
          }
        });
  }

  private RebalanceStatus.@Nullable Completed finish(
      final RebalanceRun rebalance, final @Nullable Throwable error) {
    if (running != rebalance) {
      // We stopped coordinating while the rebalance was in flight, so its state is already gone
      return null;
    }
    running = null;
    final RebalanceOutcome outcome;
    if (error != null) {
      LOG.warn("Rebalance {} failed", rebalance.id(), error);
      outcome = RebalanceOutcome.FAILED;
    } else {
      outcome = aggregateOutcome(rebalance);
    }
    final var finishedAt = clock.instant();
    rebalance.finish(finishedAt);
    final var completed =
        new RebalanceStatus.Completed(
            rebalance.id(), outcome, rebalance.partitions(), rebalance.startedAt(), finishedAt);
    if (!rebalance.dryRun()) {
      lastCompleted = completed;
      metrics.observeElapsed(outcome, Duration.between(rebalance.startedAt(), finishedAt));
    }
    LOG.info("Rebalance {} finished as {}", rebalance.id(), outcome);
    return completed;
  }

  private static RebalanceOutcome aggregateOutcome(final RebalanceRun rebalance) {
    final var anyUnsuccessful =
        rebalance.partitions().stream()
            .filter(partition -> partition.progress() == PartitionRebalanceProgress.COMPLETED)
            .map(PartitionRebalance::outcome)
            .anyMatch(outcome -> !SUCCESSFUL_PARTITION_OUTCOMES.contains(outcome));
    if (anyUnsuccessful) {
      return RebalanceOutcome.FAILED;
    }
    return rebalance.isCancelRequested() ? RebalanceOutcome.CANCELLED : RebalanceOutcome.COMPLETED;
  }

  private void updateCoordinatorRole(final CurrentClusterConfiguration clusterConfiguration) {
    if (clusterConfiguration.isUninitialized()) {
      return;
    }
    final var coordinator =
        ClusterConfigurationCoordinatorSupplier.ofMembers(clusterConfiguration.getMembers())
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
    configuration = nowCoordinating ? clusterConfiguration : null;
    final var inFlight = running;
    if (inFlight != null) {
      inFlight.observeConfiguration(clusterConfiguration);
    }
  }

  private void discardState() {
    final var inFlight = running;
    if (inFlight != null) {
      inFlight.abandon();
      LOG.warn("Abandoning rebalance {}; partitions already transferred", inFlight.id());
    }
    running = null;
    lastCompleted = null;
    metrics.stopCoordinating();
  }

  /**
   * The configuration this member coordinates under, or {@code null} - having refused {@code
   * result} - if it is not the coordinator.
   */
  private @Nullable CurrentClusterConfiguration coordinatedConfiguration(
      final ActorFuture<?> result) {
    final var current = configuration;
    if (current == null) {
      result.completeExceptionally(
          new NotCoordinatorException(
              "Member %s is not the rebalancing coordinator".formatted(localMemberId.id())));
    }
    return current;
  }

  private RebalanceStatus status() {
    return status(lastCompleted);
  }

  private RebalanceStatus status(final RebalanceStatus.@Nullable Completed completed) {
    final var inFlight = running;
    final var runningStatus = inFlight == null ? null : runningSnapshot(inFlight);
    return new RebalanceStatus(runningStatus, completed, leadershipStatus(inFlight));
  }

  private static RebalanceStatus.Running runningSnapshot(final RebalanceRun rebalance) {
    return new RebalanceStatus.Running(
        rebalance.id(),
        rebalance.overrides(),
        rebalance.dryRun(),
        rebalance.isCancelRequested(),
        rebalance.partitions(),
        rebalance.startedAt());
  }

  private ClusterLeadershipStatus leadershipStatus(final @Nullable RebalanceRun inFlight) {
    final var transferring =
        inFlight == null
            ? null
            : inFlight.partitions().stream()
                .filter(
                    partition -> partition.progress() == PartitionRebalanceProgress.TRANSFERRING)
                .findFirst()
                .orElse(null);
    return balancePlanner.leadershipStatus(Objects.requireNonNull(configuration), transferring);
  }
}

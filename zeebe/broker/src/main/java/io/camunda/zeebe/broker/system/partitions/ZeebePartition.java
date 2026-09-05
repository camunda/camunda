/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.atomix.raft.LeadershipTransferWriteBarrier;
import io.atomix.raft.RaftRoleChangeListener;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.SnapshotReplicationListener;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.partitioning.PartitionAdminAccess;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.system.monitoring.HealthMetrics;
import io.camunda.zeebe.broker.system.partitions.impl.RecoverablePartitionTransitionException;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.rebalance.ClusterConfigurationCoordinatorCheck;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.health.CriticalComponentsHealthMonitor;
import io.camunda.zeebe.scheduler.startup.StartupProcess;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;

public final class ZeebePartition extends Actor
    implements RaftRoleChangeListener,
        HealthMonitorable,
        FailureListener,
        DiskSpaceUsageListener,
        SnapshotReplicationListener {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final StartupProcess<PartitionStartupContext> startupProcess;

  private Role raftRole;
  private final List<FailureListener> failureListeners;
  private final HealthMetrics healthMetrics;
  private final RoleMetrics roleMetrics;
  private final ZeebePartitionHealth zeebePartitionHealth;
  private PartitionContext context;
  private PartitionStartupContext startupContext;
  private final PartitionAdminAccess adminAccess;
  private final PartitionTransition transition;
  private PartitionConfigurationManager partitionConfigurationManager;

  private CompletableActorFuture<Void> closeFuture;
  private boolean closing = false;

  /** Tells the leader which node may ask it to transfer leadership. */
  private final ClusterConfigurationCoordinatorCheck coordinatorCheck;

  /** Bridges the actor-scheduled write freeze to the {@link CompletableFuture}-based Raft hook. */
  private final LeadershipTransferWriteBarrier writeBarrier =
      new LeadershipTransferWriteBarrier() {
        @Override
        public CompletableFuture<Long> freeze(final Duration timeout) {
          return toCompletableFuture(freezeForTransfer(timeout));
        }

        @Override
        public CompletableFuture<Void> unfreeze() {
          return toCompletableFuture(unfreezeAfterTransfer());
        }
      };

  public ZeebePartition(
      final PartitionStartupAndTransitionContextImpl transitionContext,
      final PartitionTransition transition,
      final List<StartupStep<PartitionStartupContext>> startupSteps) {
    super("ZeebePartition", transitionContext.partitionId());
    context = transitionContext.getPartitionContext();
    adminAccess =
        new ZeebePartitionAdminAccess(
            actor, getPartitionId(), transitionContext.getPartitionAdminControl());

    this.transition = transition;
    startupContext = transitionContext;

    startupProcess = new StartupProcess<>(LOG, startupSteps);

    transitionContext.setAdminAccess(adminAccess);
    transitionContext.setActorControl(actor);
    transitionContext.setDiskSpaceAvailable(true);

    transition.setConcurrencyControl(actor);

    transitionContext.setComponentHealthMonitor(
        new CriticalComponentsHealthMonitor(
            componentName(context.partitionId()),
            actor,
            transitionContext.getComponentTreeListener(),
            Optional.of(transitionContext.brokerHealthCheckService().componentName()),
            LOG));
    zeebePartitionHealth = new ZeebePartitionHealth(transitionContext.getPartitionId(), transition);
    healthMetrics = new HealthMetrics(transitionContext.getPartitionStartupMeterRegistry());
    healthMetrics.setUnhealthy();
    failureListeners = new ArrayList<>();
    roleMetrics =
        new RoleMetrics(
            transitionContext.getPartitionStartupMeterRegistry(),
            transitionContext.getPartitionId());
    coordinatorCheck =
        new ClusterConfigurationCoordinatorCheck(
            () ->
                transitionContext
                    .getClusterConfigurationService()
                    .getCurrentClusterConfiguration());
  }

  public static String componentName(final PartitionId partitionId) {
    return String.format("Partition-%s-%d", partitionId.group(), partitionId.number());
  }

  public PartitionAdminAccess getAdminAccess() {
    return adminAccess;
  }

  @Override
  public void onActorStarting() {
    startupProcess
        .startup(actor, startupContext)
        .onComplete(
            (newStartupContext, error) -> {
              if (error != null) {
                LOG.error(error.getMessage(), error);
                handleUnrecoverableFailure(error);
                close();
                return;
              }
              startupContext = newStartupContext;
              final var transitionContext = startupContext.createTransitionContext();

              transition.updateTransitionContext(transitionContext);

              context = transitionContext.getPartitionContext();

              partitionConfigurationManager =
                  new PartitionConfigurationManager(
                      LOG, context, transitionContext.getExportedDescriptors(), actor);

              installLeadershipTransferHooks();
            });
  }

  /**
   * Installs the leadership-transfer write barrier and coordinator check on the Raft thread before
   * registering as a role-change listener, so a transfer request can never observe this partition
   * without its coordinator check in place.
   */
  private void installLeadershipTransferHooks() {
    final var server = context.getRaftPartition().getServer();
    server.setLeadershipTransferWriteBarrier(writeBarrier);
    server
        .setLeadershipTransferCoordinatorCheck(coordinatorCheck)
        .whenCompleteAsync(
            (ignored, error) -> {
              if (error != null) {
                LOG.error("Failed to install leadership-transfer coordinator check", error);
                handleUnrecoverableFailure(error);
                close();
                return;
              }
              registerListeners();
            },
            actor);
  }

  @Override
  protected void onActorStarted() {
    context.getComponentHealthMonitor().startMonitoring();
    context.getComponentHealthMonitor().registerComponent(context.getRaftPartition());
    // Add a component that keep track of health of ZeebePartition. This way
    // criticalComponentsHealthMonitor can monitor the health of ZeebePartition similar to other
    // components.
    context.getComponentHealthMonitor().registerComponent(zeebePartitionHealth);
  }

  @Override
  protected void onActorClosing() {
    // Already transitioned to inactive
    startupProcess
        .shutdown(actor, startupContext)
        .onComplete(
            (newStartupContext, error) -> {
              if (error != null) {
                LOG.error(error.getMessage(), error);
              }

              closeFuture.complete(null);
            });
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    if (closeFuture != null) {
      return closeFuture;
    }

    closeFuture = new CompletableActorFuture<>();

    actor.run(
        () -> {
          LOG.debug("Closing ZeebePartition {}", context.getPartitionId());
          closing = true;

          removeListeners();
          context.getComponentHealthMonitor().removeComponent(zeebePartitionHealth);
          context.getComponentHealthMonitor().removeComponent(context.getRaftPartition());

          final var inactiveTransitionFuture = transitionToInactive();

          // allows to await current transition to avoid concurrent modifications and
          // transitioning
          inactiveTransitionFuture.onComplete((nothing, err) -> super.closeAsync());
        });

    return closeFuture;
  }

  @Override
  protected void handleFailure(final Throwable failure) {
    LOG.warn("Uncaught exception in {}.", getName(), failure);
    // Most probably exception happened in the middle of installing leader or follower services
    // because this actor is not doing anything else
    onInstallFailure(failure);
  }

  private static <T> CompletableFuture<T> toCompletableFuture(final ActorFuture<T> actorFuture) {
    final var future = new CompletableFuture<T>();
    actorFuture.onComplete(
        (result, error) -> {
          if (error != null) {
            future.completeExceptionally(error);
          } else {
            future.complete(result);
          }
        });
    return future;
  }

  @Override
  public String componentName() {
    return componentName(context.partitionId());
  }

  @Override
  public HealthReport getHealthReport() {
    return context.getComponentHealthMonitor().getHealthReport();
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    actor.run(
        () -> {
          failureListeners.add(failureListener);
          if (getHealthReport().getStatus() == HealthStatus.HEALTHY) {
            failureListener.onRecovered(getHealthReport());
          } else {
            failureListener.onFailure(getHealthReport());
          }
        });
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    actor.run(() -> failureListeners.remove(failureListener));
  }

  /**
   * Called by atomix on role change.
   *
   * @param newRole the new role of the raft partition
   */
  @Override
  @Deprecated // will be removed from public API of ZeebePartition
  public void onNewRole(final Role newRole, final long newTerm) {
    actor.run(
        () -> {
          if (!closing) {
            onRoleChange(newRole, newTerm);
          }
        });
  }

  private void onRoleChange(final Role newRole, final long newTerm) {
    // The transfer-pause flag and the write gate both outlive role transitions, so a transfer that
    // ends by stepping down (e.g. the watchdog firing) could leave either stuck paused. Clear both
    // explicitly on every transition rather than relying on teardown/rebuild.
    context.setPausedForTransfer(false);
    if (context.getLogStream() != null) {
      context.getLogStream().resumeWrites();
    }
    switch (newRole) {
      case LEADER:
        leaderTransition(newTerm);
        break;
      case INACTIVE:
        transitionToInactive();
        break;
      case PASSIVE:
      case PROMOTABLE:
      case CANDIDATE:
      case FOLLOWER:
      default:
        followerTransition(newTerm);
        break;
    }
    LOG.debug("Partition role transitioning from {} to {} in term {}", raftRole, newRole, newTerm);
    raftRole = newRole;
  }

  private ActorFuture<Void> leaderTransition(final long newTerm) {
    final var latencyTimer = roleMetrics.startLeaderTransitionLatencyTimer();
    context.notifyListenersOfBecameRaftLeader(newTerm);
    final var leaderTransitionFuture = transition.toLeader(newTerm);
    leaderTransitionFuture.onComplete(
        (success, error) -> {
          if (error == null) {
            latencyTimer.close();
            final List<ActorFuture<Void>> listenerFutures =
                context.notifyListenersOfBecomingLeader(newTerm);
            actor.runOnCompletion(
                listenerFutures,
                t -> {
                  if (t != null) {
                    onInstallFailure(t);
                  } else {
                    onRecoveredInternal();
                  }
                });
          } else {
            onInstallFailure(error);
          }
        });
    return leaderTransitionFuture;
  }

  private ActorFuture<Void> followerTransition(final long newTerm) {
    context.notifyListenersOfBecameRaftFollower(newTerm);
    final var followerTransitionFuture = transition.toFollower(newTerm);
    followerTransitionFuture.onComplete(
        (success, error) -> {
          if (error == null) {
            final List<ActorFuture<Void>> listenerFutures =
                context.notifyListenersOfBecomingFollower(newTerm);
            actor.runOnCompletion(
                listenerFutures,
                t -> {
                  // Compare with the current term in case a new role transition happened
                  if (t != null) {
                    onInstallFailure(t);
                  } else {
                    onRecoveredInternal();
                  }
                });
          } else {
            onInstallFailure(error);
          }
        });
    return followerTransitionFuture;
  }

  private ActorFuture<Void> transitionToInactive() {
    zeebePartitionHealth.setServicesInstalled(false);
    final var transitionFuture = transition.toInactive(context.getCurrentTerm());
    transitionFuture.onComplete(
        (success, error) -> {
          if (error != null) {
            onInstallFailure(error);
          }
        });
    return transitionFuture;
  }

  private void registerListeners() {
    context.getRaftPartition().addRoleChangeListener(this);
    context.getComponentHealthMonitor().addFailureListener(this);
    context.getRaftPartition().getServer().addSnapshotReplicationListener(this);
  }

  private void removeListeners() {
    context.getRaftPartition().removeRoleChangeListener(this);
    context.getComponentHealthMonitor().removeFailureListener(this);
    context.getRaftPartition().getServer().removeSnapshotReplicationListener(this);
  }

  @Override
  @Deprecated // will be removed from public API of ZeebePartition
  public void onFailure(final HealthReport report) {
    actor.run(
        () -> {
          healthMetrics.setUnhealthy();
          failureListeners.forEach((l) -> l.onFailure(report));
        });
  }

  @Override
  @Deprecated // will be removed from public API of ZeebePartition
  public void onRecovered(final HealthReport report) {
    actor.run(
        () -> {
          healthMetrics.setHealthy();
          failureListeners.forEach(l -> l.onRecovered(report));
        });
  }

  @Override
  @Deprecated // will be removed from public API of ZeebePartition
  public void onUnrecoverableFailure(final HealthReport report) {
    // TODO: Can't use null here
    actor.run(() -> handleUnrecoverableFailure(null));
  }

  private void onInstallFailure(final Throwable error) {
    if (error instanceof UnrecoverableException) {
      LOG.error(
          "Failed to install partition {} (role {}, term {}) with unrecoverable failure: ",
          context.getPartitionId(),
          context.getCurrentRole(),
          context.getCurrentTerm(),
          error);
      handleUnrecoverableFailure(error);
    } else if (error instanceof RecoverablePartitionTransitionException) {
      LOG.info(
          "Aborted installation of partition {}, cause: {}",
          context.getPartitionId(),
          error.getMessage());
    } else {
      LOG.error("Failed to install partition {}", context.getPartitionId(), error);
      handleRecoverableFailure();
    }
  }

  private void handleRecoverableFailure() {
    zeebePartitionHealth.setServicesInstalled(false);
    context.notifyListenersOfBecomingInactive();

    // If RaftPartition has already transition to a new role in a new term, we can ignore this
    // failure. The transition for the higher term will be already enqueued and services will be
    // installed for the new role.
    if (isCurrentLeaderTerm()) {
      LOG.info(
          "Unexpected failure occurred in partition {} (role {}, term {}), stepping down",
          context.getPartitionId(),
          context.getCurrentRole(),
          context.getCurrentTerm());
      context.getRaftPartition().stepDown();
    } else if (context.getCurrentRole() == Role.FOLLOWER) {
      LOG.info(
          "Unexpected failure occurred in partition {} (role {}, term {}), transitioning to inactive",
          context.getPartitionId(),
          context.getCurrentRole(),
          context.getCurrentTerm());
      stopPartitionOnError();
    }
  }

  /** Whether this node is still leader in the term it was leader in when the caller checked. */
  private boolean isCurrentLeaderTerm() {
    return context.getCurrentRole() == Role.LEADER
        && context.getCurrentTerm() == context.getRaftPartition().term();
  }

  private void handleUnrecoverableFailure(final Throwable error) {
    final var instant = ActorClock.current().instant();

    final var report = HealthReport.dead(this).withIssue(error, instant);
    healthMetrics.setDead();
    zeebePartitionHealth.onUnrecoverableFailure(error);
    stopPartitionOnError();
    failureListeners.forEach(l -> l.onUnrecoverableFailure(report));
  }

  private void stopPartitionOnError() {
    context.getRaftPartition().removeRoleChangeListener(this);
    transitionToInactive()
        .onComplete(
            (ignore, e) -> {
              if (e != null) {
                LOG.warn("Failed to transition to inactive. Stopping raft partition anyway.");
              }
              context.notifyListenersOfBecomingInactive();
              context.getRaftPartition().stop();
            });
  }

  private void onRecoveredInternal() {
    zeebePartitionHealth.setServicesInstalled(true);
  }

  @Override
  @Deprecated // currently the implementation forwards this to other components inside the
  // partition; these components will be directly registered as listeners in the future
  public void onDiskSpaceNotAvailable() {
    actor.call(
        () -> {
          context.setDiskSpaceAvailable(false);
          zeebePartitionHealth.setDiskSpaceAvailable(false);
          if (context.getStreamProcessor() != null) {
            LOG.warn("Disk space usage is above threshold. Pausing stream processor.");
            context.getStreamProcessor().pauseProcessing();
          }
        });
  }

  @Override
  @Deprecated // currently the implementation forwards this to other components inside the
  // partition; these components will be directly registered as listeners in the future
  public void onDiskSpaceAvailable() {
    actor.call(
        () -> {
          context.setDiskSpaceAvailable(true);
          zeebePartitionHealth.setDiskSpaceAvailable(true);
          if (context.getStreamProcessor() != null && context.shouldProcess()) {
            LOG.info("Disk space usage is below threshold. Resuming stream processor.");
            context.getStreamProcessor().resumeProcessing();
          }
        });
  }

  /**
   * Freezes this partition's writes for a leadership transfer: pauses processing so no follow-up
   * commands are written, then freezes write admission. The Raft term is kept, and both replication
   * and exporters keep running. The Raft-side pause - the frozen catch-up target and the step-down
   * watchdog - is armed by the transfer itself once this freeze is in place.
   *
   * <p>The future completes with the epoch millis at which write admission was frozen.
   *
   * <p>The caller must serialize leadership transfer operations for a given partition, and must
   * await completion (success or failure) of this future before calling {@link
   * #unfreezeAfterTransfer()}. It is not safe to interleave this method with itself or {@code
   * unfreezeAfterTransfer()}.
   */
  public ActorFuture<Long> freezeForTransfer(final Duration timeout) {
    final CompletableActorFuture<Long> result = new CompletableActorFuture<>();
    actor.run(
        () -> {
          LOG.info(
              "Freezing partition {} writes for leadership transfer", context.getPartitionId());
          // Make the transfer pause visible to shouldProcess(), so a disk/admin resume mid-transfer
          // cannot silently un-pause the partition.
          context.setPausedForTransfer(true);
          // If the freeze does not complete within the timeout, roll back and fail so a stalled
          // processor pause cannot leave the partition half-frozen. This bounds arming the freeze
          // only; how long the partition may then stay frozen is the Raft watchdog's budget.
          final ScheduledTimer barrierTimeout =
              actor.schedule(timeout.toMillis(), () -> onBarrierTimeout(timeout, result));
          result.onComplete((ok, error) -> barrierTimeout.cancel());
          // Await the processor confirming paused: once done, every write it submitted has already
          // enqueued its Raft append.
          context
              .getStreamProcessor()
              .pauseProcessing()
              .onComplete(
                  (ignored, error) -> {
                    if (result.isDone()) {
                      // Barrier already timed out and rolled back; ignore the late processor
                      // result.
                      return;
                    }

                    if (error != null) {
                      rollbackTransferPause();
                      result.completeExceptionally(error);
                      return;
                    }

                    // Freeze write admission and drain in-flight writes, so no entry can be
                    // appended past this point
                    context.getLogStream().pauseWrites();
                    result.complete(ActorClock.currentTimeMillis());
                  });
        });
    return result;
  }

  /** Rolls back a write freeze that did not complete within its timeout. */
  private void onBarrierTimeout(final Duration timeout, final CompletableActorFuture<Long> result) {
    if (result.isDone()) {
      return;
    }
    LOG.warn(
        "Leadership-transfer write freeze for partition {} did not complete within {}; rolling "
            + "back",
        context.getPartitionId(),
        timeout);
    rollbackTransferPause();
    result.completeExceptionally(
        new TimeoutException("Timed out establishing the leadership-transfer write freeze"));
  }

  /**
   * Undoes {@link #freezeForTransfer(Duration)} so a freeze that fails partway does not leave the
   * partition write-frozen.
   */
  private void rollbackTransferPause() {
    context.setPausedForTransfer(false);
    context.getLogStream().resumeWrites();
    final var streamProcessor = context.getStreamProcessor();
    if (streamProcessor != null && context.shouldProcess()) {
      streamProcessor.resumeProcessing();
    }
  }

  /**
   * Reopens this partition's writes after a coordinated leadership transfer, undoing {@link
   * #freezeForTransfer(Duration)}. Tolerates a freeze that failed or never completed.
   *
   * <p>The attempt always ends either fully reopened or, if reopening fails, stepped down.
   *
   * <p>Must not interleave with a {@link #freezeForTransfer(Duration)} whose future has not yet
   * completed.
   */
  public ActorFuture<Void> unfreezeAfterTransfer() {
    final CompletableActorFuture<Void> result = new CompletableActorFuture<>();
    actor.run(
        () -> {
          LOG.info(
              "Reopening partition {} writes after leadership transfer", context.getPartitionId());
          reopen(result);
        });
    return result;
  }

  /** Reopens write admission, the transfer-pause flag and the stream processor. */
  private void reopen(final CompletableActorFuture<Void> result) {
    context.getLogStream().resumeWrites();
    context.setPausedForTransfer(false);
    final ActorFuture<Void> processorResume;
    if (context.getStreamProcessor() != null && context.shouldProcess()) {
      processorResume = context.getStreamProcessor().resumeProcessing();
    } else {
      processorResume = CompletableActorFuture.completed(null);
    }
    processorResume.onComplete(
        (resumed, processorError) -> {
          if (processorError != null) {
            LOG.warn(
                "Failed to resume the stream processor for partition {} after a transfer; stepping "
                    + "down to rebuild services",
                context.getPartitionId(),
                processorError);
            if (isCurrentLeaderTerm()) {
              context.getRaftPartition().stepDown();
            }
            result.completeExceptionally(processorError);
          } else {
            result.complete(null);
          }
        });
  }

  public int getPartitionId() {
    return context.getPartitionId();
  }

  public PersistedSnapshotStore getSnapshotStore() {
    return context.getRaftPartition().getServer().getPersistedSnapshotStore();
  }

  public ActorFuture<Optional<StreamProcessor>> getStreamProcessor() {
    return actor.call(() -> Optional.ofNullable(context.getStreamProcessor()));
  }

  public ActorFuture<Optional<ExporterDirector>> getExporterDirector() {
    return actor.call(() -> Optional.ofNullable(context.getExporterDirector()));
  }

  @Override
  public void onSnapshotReplicationStarted() {
    // When a snapshot is received, the follower stream processor and exporter should
    // restart from a new state. So we transition to Inactive to close existing services. The
    // services will be reinstalled when snapshot replication is completed.
    // We do not want to mark it as unhealthy, hence we don't reuse transitionToInactive()
    actor.run(
        () -> {
          if (closing) {
            return;
          }
          transition
              .toInactive(context.getCurrentTerm())
              .onComplete(
                  (success, error) -> {
                    if (error != null) {
                      onInstallFailure(error);
                    }
                  });
        });
  }

  @Override
  public void onSnapshotReplicationCompleted(final long term) {
    // Snapshot is received only by the followers. Hence we can safely assume that we have to
    // re-install follower services.
    actor.run(
        () -> {
          if (!closing) {
            followerTransition(term);
          }
        });
  }

  public ActorFuture<Role> getCurrentRole() {
    return actor.call(() -> context.getCurrentRole());
  }

  public ActorFuture<Void> disableExporter(final String exporterId) {
    final var future = new CompletableActorFuture<Void>();
    actor.run(() -> partitionConfigurationManager.disableExporter(exporterId).onComplete(future));
    return future;
  }

  public ActorFuture<Void> deleteExporter(final String exporterId) {
    final var future = new CompletableActorFuture<Void>();
    actor.run(() -> partitionConfigurationManager.deleteExporter(exporterId).onComplete(future));
    return future;
  }

  public ActorFuture<Void> enableExporter(
      final String exporterId, final long metadataVersion, final String initializeFrom) {
    final var future = new CompletableActorFuture<Void>();
    actor.run(
        () ->
            partitionConfigurationManager
                .enableExporter(exporterId, metadataVersion, initializeFrom)
                .onComplete(future));
    return future;
  }

  public ActorFuture<Void> setExportingState(final ExportingState exportingState) {
    final var future = new CompletableActorFuture<Void>();
    actor.run(
        () -> partitionConfigurationManager.setExportingState(exportingState).onComplete(future));
    return future;
  }
}

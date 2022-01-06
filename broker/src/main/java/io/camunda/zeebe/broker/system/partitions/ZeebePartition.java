/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.atomix.raft.RaftRoleChangeListener;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.SnapshotReplicationListener;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.partitioning.PartitionAdminAccess;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.system.monitoring.HealthMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.CriticalComponentsHealthMonitor;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import io.camunda.zeebe.util.startup.StartupProcess;
import io.camunda.zeebe.util.startup.StartupStep;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private final String actorName;
  private final List<FailureListener> failureListeners;
  private final HealthMetrics healthMetrics;
  private final RoleMetrics roleMetrics;
  private final ZeebePartitionHealth zeebePartitionHealth;
  private PartitionContext context;
  private PartitionStartupContext startupContext;
  private final PartitionAdminControl adminControl;
  private final PartitionTransition transition;
  private CompletableActorFuture<Void> closeFuture;
  private boolean closing = false;

  public ZeebePartition(
      final PartitionStartupAndTransitionContextImpl transitionContext,
      final PartitionTransition transition,
      final List<StartupStep<PartitionStartupContext>> startupSteps) {
    context = transitionContext.getPartitionContext();
    adminControl = transitionContext.getPartitionAdminControl();

    this.transition = transition;
    startupContext = transitionContext;

    startupProcess = new StartupProcess<>(LOG, startupSteps);

    transitionContext.setActorControl(actor);
    transitionContext.setDiskSpaceAvailable(true);

    transition.setConcurrencyControl(actor);

    actorName =
        buildActorName(
            transitionContext.getNodeId(), "ZeebePartition", transitionContext.getPartitionId());
    transitionContext.setComponentHealthMonitor(
        new CriticalComponentsHealthMonitor(
            "Partition-" + transitionContext.getPartitionId(), actor, LOG));
    zeebePartitionHealth = new ZeebePartitionHealth(transitionContext.getPartitionId());
    healthMetrics = new HealthMetrics(transitionContext.getPartitionId());
    healthMetrics.setUnhealthy();
    failureListeners = new ArrayList<>();
    roleMetrics = new RoleMetrics(transitionContext.getPartitionId());
  }

  public PartitionAdminAccess createAdminAccess() {
    return new ZeebePartitionAdminAccess(actor, adminControl);
  }

  @Override
  protected Map<String, String> createContext() {
    final var actorContext = super.createContext();
    actorContext.put(ACTOR_PROP_PARTITION_ID, Integer.toString(context.getPartitionId()));
    return actorContext;
  }

  @Override
  public String getName() {
    return actorName;
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

              registerListeners();
            });
  }

  @Override
  protected void onActorStarted() {
    context.getComponentHealthMonitor().startMonitoring();
    context
        .getComponentHealthMonitor()
        .registerComponent(context.getRaftPartition().name(), context.getRaftPartition());
    // Add a component that keep track of health of ZeebePartition. This way
    // criticalComponentsHealthMonitor can monitor the health of ZeebePartition similar to other
    // components.
    context
        .getComponentHealthMonitor()
        .registerComponent(zeebePartitionHealth.getName(), zeebePartitionHealth);
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

              // reset contexts to null to not have lingering references that could
              // cause OOM problems in tests which start/stop partitions
              startupContext = null;
              context = null;

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
          context.getComponentHealthMonitor().removeComponent(zeebePartitionHealth.getName());

          final var inactiveTransitionFuture = transitionToInactive();

          // allows to await current transition to avoid concurrent modifications and
          // transitioning
          inactiveTransitionFuture.onComplete((nothing, err) -> super.closeAsync());
        });

    return closeFuture;
  }

  @Override
  protected void handleFailure(final Throwable failure) {
    LOG.warn("Uncaught exception in {}.", actorName, failure);
    // Most probably exception happened in the middle of installing leader or follower services
    // because this actor is not doing anything else
    onInstallFailure(failure);
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
                  }
                });
            onRecoveredInternal();
          } else {
            LOG.error("Failed to install leader partition {}", context.getPartitionId(), error);
            onInstallFailure(error);
          }
        });
    return leaderTransitionFuture;
  }

  private ActorFuture<Void> followerTransition(final long newTerm) {
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
                  }
                });
            onRecoveredInternal();
          } else {
            LOG.error("Failed to install follower partition {}", context.getPartitionId(), error);
            onInstallFailure(error);
          }
        });
    return followerTransitionFuture;
  }

  private ActorFuture<Void> transitionToInactive() {
    zeebePartitionHealth.setServicesInstalled(false);
    return transition.toInactive(context.getCurrentTerm());
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
  public void onRecovered() {
    actor.run(
        () -> {
          healthMetrics.setHealthy();
          failureListeners.forEach(FailureListener::onRecovered);
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
    } else {
      handleRecoverableFailure();
    }
  }

  private void handleRecoverableFailure() {
    zeebePartitionHealth.setServicesInstalled(false);
    context.notifyListenersOfBecomingInactive();

    // If RaftPartition has already transition to a new role in a new term, we can ignore this
    // failure. The transition for the higher term will be already enqueued and services will be
    // installed for the new role.
    if (context.getCurrentRole() == Role.LEADER
        && context.getCurrentTerm() == context.getRaftPartition().term()) {
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
      context.getRaftPartition().goInactive();
    }
  }

  private void handleUnrecoverableFailure(final Throwable error) {
    final var report = HealthReport.dead(this).withIssue(error);
    healthMetrics.setDead();
    zeebePartitionHealth.onUnrecoverableFailure(error);
    transitionToInactive();
    context.getRaftPartition().goInactive();
    failureListeners.forEach((l) -> l.onUnrecoverableFailure(report));
    context.notifyListenersOfBecomingInactive();
  }

  private void onRecoveredInternal() {
    zeebePartitionHealth.setServicesInstalled(true);
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
            failureListener.onRecovered();
          } else {
            failureListener.onFailure(getHealthReport());
          }
        });
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    actor.run(() -> failureListeners.remove(failureListener));
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
          zeebePartitionHealth.setDiskSpaceAvailable(false);
          if (context.getStreamProcessor() != null && context.shouldProcess()) {
            LOG.info("Disk space usage is below threshold. Resuming stream processor.");
            context.getStreamProcessor().resumeProcessing();
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
          if (!closing) {
            transition.toInactive(context.getCurrentTerm());
          }
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
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.camunda.zeebe.broker.bootstrap.BrokerStartupContext;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the transition of a single physical tenant's partition group between processing ({@link
 * PartitionManagerImpl}) and recovery ({@link RecoveryPartitionManager}) mode.
 *
 * <p>One handler is built per physical tenant by the {@link
 * io.camunda.zeebe.broker.bootstrap.PartitionManagerStep} that owns the tenant, reusing that
 * tenant's {@link TopologyManagerImpl}, and is closed when the step shuts the partition manager
 * down. Only the default tenant participates in cluster configuration changes, so only its handler
 * registers as the broker's {@link ModeChangeExecutor} and stays registered across mode changes. A
 * transition stops the active manager, recreates it in the target mode, starts it, and re-publishes
 * it on the {@link BrokerStartupContext}.
 *
 * <p>The handler keeps no partition manager reference of its own; the active manager and the
 * remaining dependencies are resolved from the {@link BrokerStartupContext} on demand.
 */
public final class PartitionModeHandler implements ModeChangeExecutor, AsyncClosable {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionModeHandler.class);

  private final BrokerStartupContext brokerStartupContext;
  private final String partitionGroup;
  private final TopologyManagerImpl topologyManager;

  public PartitionModeHandler(
      final BrokerStartupContext brokerStartupContext,
      final String partitionGroup,
      final TopologyManagerImpl topologyManager) {
    this.brokerStartupContext = brokerStartupContext;
    this.partitionGroup = partitionGroup;
    this.topologyManager = topologyManager;
  }

  /**
   * Registers this handler as the broker's mode change executor. Only the default tenant
   * participates in cluster configuration changes, so only its handler registers. The partition
   * change executors are registered by the partition manager itself on {@link
   * PartitionManager#start()}.
   */
  public void register() {
    if (!isDefaultGroup()) {
      return;
    }
    clusterConfigurationService().registerModeChangeExecutor(this);
  }

  @Override
  public ActorFuture<Void> enterRecovery() {
    final var concurrencyControl = concurrencyControl();
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          if (isRecovering()) {
            result.completeExceptionally(
                new IllegalStateException(
                    "Cannot enter recovery for partition group %s — already in recovery mode"
                        .formatted(partitionGroup)));
            return;
          }
          transitionTo(Mode.RECOVERING, result);
        });
    return result;
  }

  @Override
  public ActorFuture<Void> exitRecovery() {
    final var concurrencyControl = concurrencyControl();
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          if (!isRecovering()) {
            result.completeExceptionally(
                new IllegalStateException(
                    "Cannot exit recovery for partition group %s — already in processing mode"
                        .formatted(partitionGroup)));
            return;
          }
          transitionTo(Mode.PROCESSING, result);
        });
    return result;
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    final var concurrencyControl = concurrencyControl();
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          if (isDefaultGroup()) {
            clusterConfigurationService().removeModeChangeExecutor();
          }
          result.complete(null);
        });
    return result;
  }

  private void transitionTo(final Mode mode, final ActorFuture<Void> result) {
    final var current = currentManager();
    if (current == null) {
      result.completeExceptionally(
          new IllegalArgumentException(
              "No partition manager for partition group %s".formatted(partitionGroup)));
      return;
    }

    final var concurrencyControl = concurrencyControl();
    LOG.info("Transitioning partition group {} to {} mode", partitionGroup, mode);
    concurrencyControl.runOnCompletion(
        current.stop(),
        (ignored, stopError) -> {
          if (stopError != null) {
            result.completeExceptionally(stopError);
            return;
          }
          final var manager = createPartitionManager(mode);
          concurrencyControl.runOnCompletion(
              manager.start(),
              (ignore, startError) -> {
                if (startError != null) {
                  result.completeExceptionally(startError);
                  return;
                }
                brokerStartupContext.addPartitionManager(partitionGroup, manager);
                LOG.info("Partition group {} transitioned to {} mode", partitionGroup, mode);
                result.complete(null);
              });
        });
  }

  private PartitionManager createPartitionManager(final Mode mode) {
    return mode == Mode.RECOVERING
        ? PartitionManager.createRecoveryPartitionManager(
            brokerStartupContext, partitionGroup, topologyManager)
        : PartitionManager.createPartitionManager(
            brokerStartupContext, partitionGroup, topologyManager);
  }

  private PartitionManager currentManager() {
    return brokerStartupContext.getPartitionManagers().get(partitionGroup);
  }

  private boolean isRecovering() {
    return currentManager() instanceof RecoveryPartitionManager;
  }

  private boolean isDefaultGroup() {
    return PartitionManager.isDefaultPhysicalTenant(partitionGroup);
  }

  private ConcurrencyControl concurrencyControl() {
    return brokerStartupContext.getConcurrencyControl();
  }

  private ClusterConfigurationService clusterConfigurationService() {
    return brokerStartupContext.getClusterConfigurationService();
  }
}

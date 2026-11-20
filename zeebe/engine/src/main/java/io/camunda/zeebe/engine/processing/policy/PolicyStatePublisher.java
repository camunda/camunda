/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.zeebe.engine.processing.policy.PolicyStatus.UpdateStatus;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.state.immutable.PolicyState;
import io.camunda.zeebe.engine.state.immutable.PolicyUpdateState;
import io.camunda.zeebe.engine.state.policy.PersistedPolicyUpdate;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import org.jspecify.annotations.NullMarked;

/**
 * Publishes the policy state of partition 1 through the {@link PolicyStateNotifier}. The policy
 * processors call it after they changed the state; the state is read while the command is processed
 * and published once the command is committed, which only happens on the leader. As a new leader
 * replays without side effects, it also publishes the state once it has recovered.
 */
@NullMarked
final class PolicyStatePublisher implements StreamProcessorLifecycleAware {

  private final PolicyState policyState;
  private final PolicyUpdateState policyUpdateState;
  private final PolicyStateNotifier notifier;
  private final boolean deploymentPartition;

  PolicyStatePublisher(
      final PolicyState policyState,
      final PolicyUpdateState policyUpdateState,
      final PolicyStateNotifier notifier,
      final int partitionId) {
    this.policyState = policyState;
    this.policyUpdateState = policyUpdateState;
    this.notifier = notifier;
    deploymentPartition = partitionId == Protocol.DEPLOYMENT_PARTITION;
  }

  /** Publishes the state as it is after the current command, once the command is committed. */
  void publishAfterCommit(final SideEffectWriter sideEffectWriter) {
    if (deploymentPartition) {
      final var status = currentStatus();
      sideEffectWriter.appendSideEffect(() -> notifier.publish(status));
    }
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    if (deploymentPartition) {
      notifier.publish(currentStatus());
    }
  }

  @Override
  public void onClose() {
    clear();
  }

  @Override
  public void onFailed() {
    clear();
  }

  private void clear() {
    // the other partitions of the physical tenant share the notifier, but never publish
    if (deploymentPartition) {
      notifier.clear();
    }
  }

  private PolicyStatus currentStatus() {
    final var openUpdate = policyUpdateState.getOpenUpdate();
    return new PolicyStatus(
        policyState.getCurrentPolicy().orElse(null),
        openUpdate.map(PolicyStatePublisher::statusOf).orElse(UpdateStatus.NONE),
        openUpdate.map(update -> update.getUpdate().getUpdateId()).orElse(null));
  }

  private static UpdateStatus statusOf(final PersistedPolicyUpdate update) {
    return update.isApplying() ? UpdateStatus.APPLYING : UpdateStatus.PENDING;
  }
}

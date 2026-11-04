/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.PolicyState;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

/**
 * Commits a policy version: the new policy becomes visible on the partition. Partition 1 switches
 * after the last chunk of an update is applied and distributes the switch on the identity queue.
 * The update's identity commands were distributed on the same queue before, so every other
 * partition switches only after it has applied the whole update.
 *
 * <p>A partition that is already at this version or a newer one rejects the switch, and still
 * acknowledges a distributed switch so that the distribution completes.
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class PolicyUpdateSwitchVersionProcessor
    implements DistributedTypedRecordProcessor<PolicyUpdateRecord> {

  private final PolicyState policyState;
  private final KeyGenerator keyGenerator;
  private final CommandDistributionBehavior distributionBehavior;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;

  public PolicyUpdateSwitchVersionProcessor(
      final PolicyState policyState,
      final KeyGenerator keyGenerator,
      final CommandDistributionBehavior distributionBehavior,
      final Writers writers) {
    this.policyState = policyState;
    this.keyGenerator = keyGenerator;
    this.distributionBehavior = distributionBehavior;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processNewCommand(final TypedRecord<PolicyUpdateRecord> command) {
    final var notInternal = PolicyUpdateOrigin.checkInternal(command);
    if (notInternal.isPresent()) {
      rejectionWriter.appendRejection(
          command, notInternal.get().type(), notInternal.get().reason());
      return;
    }
    if (rejectIfNotNewer(command)) {
      return;
    }

    final long key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, PolicyUpdateIntent.VERSION_SWITCHED, command.getValue());
    distributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<PolicyUpdateRecord> command) {
    // a client request with a key of another partition would otherwise look like a distribution
    final var fromClient = PolicyUpdateOrigin.checkNotFromClient(command);
    if (fromClient.isPresent()) {
      rejectionWriter.appendRejection(command, fromClient.get().type(), fromClient.get().reason());
      return;
    }
    if (!rejectIfNotNewer(command)) {
      stateWriter.appendFollowUpEvent(
          command.getKey(), PolicyUpdateIntent.VERSION_SWITCHED, command.getValue());
    }
    distributionBehavior.acknowledgeCommand(command);
  }

  private boolean rejectIfNotNewer(final TypedRecord<PolicyUpdateRecord> command) {
    final var update = command.getValue();
    final long currentVersion = policyState.getCurrentPolicyVersion();
    if (update.getPolicyVersion() > currentVersion) {
      return false;
    }
    rejectionWriter.appendRejection(
        command,
        RejectionType.INVALID_STATE,
        "Expected to switch to policy version %d of policy update '%s', but the current policy version is already %d"
            .formatted(update.getPolicyVersion(), update.getUpdateId(), currentVersion));
    return true;
  }
}

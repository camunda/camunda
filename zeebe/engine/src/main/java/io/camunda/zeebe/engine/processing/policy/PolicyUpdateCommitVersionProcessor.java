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
import io.camunda.zeebe.engine.state.immutable.PolicyUpdateState;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

/**
 * Commits a policy version: the new policy becomes visible on the partition. Partition 1 commits
 * after the last chunk of an update is applied and distributes the commit on the identity queue.
 * The update's identity commands were distributed on the same queue before, so every other
 * partition commits only after it has applied the whole update.
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class PolicyUpdateCommitVersionProcessor
    implements DistributedTypedRecordProcessor<PolicyUpdateRecord> {

  private final PolicyUpdateState policyUpdateState;
  private final KeyGenerator keyGenerator;
  private final CommandDistributionBehavior distributionBehavior;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;

  public PolicyUpdateCommitVersionProcessor(
      final PolicyUpdateState policyUpdateState,
      final KeyGenerator keyGenerator,
      final CommandDistributionBehavior distributionBehavior,
      final Writers writers) {
    this.policyUpdateState = policyUpdateState;
    this.keyGenerator = keyGenerator;
    this.distributionBehavior = distributionBehavior;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processNewCommand(final TypedRecord<PolicyUpdateRecord> command) {
    final var notInternal = PolicyUpdateGuard.checkInternal(command);
    if (notInternal.isPresent()) {
      rejectionWriter.appendRejection(
          command, notInternal.get().type(), notInternal.get().reason());
      return;
    }

    // the commit follows the identity commands of the last chunk; if one of them was rejected, the
    // update failed and must not be committed
    final var updateId = command.getValue().getUpdateId();
    final var isOpen =
        policyUpdateState
            .getOpenUpdate()
            .filter(open -> open.getUpdate().getUpdateId().equals(updateId))
            .isPresent();
    if (!isOpen) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_STATE,
          "Expected policy update '%s' to be open to commit its version, but it failed or no longer exists"
              .formatted(updateId));
      return;
    }

    final long key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, PolicyUpdateIntent.VERSION_COMMITTED, command.getValue());
    distributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<PolicyUpdateRecord> command) {
    // a client request with a key of another partition would otherwise look like a distribution
    final var fromClient = PolicyUpdateGuard.checkNotFromClient(command);
    if (fromClient.isPresent()) {
      rejectionWriter.appendRejection(command, fromClient.get().type(), fromClient.get().reason());
      return;
    }
    stateWriter.appendFollowUpEvent(
        command.getKey(), PolicyUpdateIntent.VERSION_COMMITTED, command.getValue());
    distributionBehavior.acknowledgeCommand(command);
  }
}

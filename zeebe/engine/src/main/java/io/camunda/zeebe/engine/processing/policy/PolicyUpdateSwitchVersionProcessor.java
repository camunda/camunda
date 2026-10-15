/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

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
 * Switches a partition to a new policy version, which makes the records of that version visible.
 *
 * <p>The coordinator switches first and then distributes the command on the identity queue. The
 * entity commands of the update were distributed on the same queue before, and a queue delivers in
 * order to each partition, so every partition switches only after it applied the whole update.
 */
@NullMarked
public final class PolicyUpdateSwitchVersionProcessor
    implements DistributedTypedRecordProcessor<PolicyUpdateRecord> {

  private final PolicyUpdateState policyUpdateState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public PolicyUpdateSwitchVersionProcessor(
      final PolicyUpdateState policyUpdateState,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.policyUpdateState = policyUpdateState;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<PolicyUpdateRecord> command) {
    if (isNotNewer(command)) {
      rejectAsSwitched(command);
      return;
    }

    final long key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, PolicyUpdateIntent.VERSION_SWITCHED, command.getValue());
    commandDistributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<PolicyUpdateRecord> command) {
    // a repeated delivery switches nothing, but the distribution must still be acknowledged
    if (isNotNewer(command)) {
      rejectAsSwitched(command);
    } else {
      stateWriter.appendFollowUpEvent(
          command.getKey(), PolicyUpdateIntent.VERSION_SWITCHED, command.getValue());
    }
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private boolean isNotNewer(final TypedRecord<PolicyUpdateRecord> command) {
    return command.getValue().getPolicyVersion() <= policyUpdateState.getCurrentVersion();
  }

  private void rejectAsSwitched(final TypedRecord<PolicyUpdateRecord> command) {
    rejectionWriter.appendRejection(
        command,
        RejectionType.ALREADY_EXISTS,
        "Expected to switch to policy version %d, but version %d is already current"
            .formatted(
                command.getValue().getPolicyVersion(), policyUpdateState.getCurrentVersion()));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.PolicyState;
import io.camunda.zeebe.engine.state.immutable.PolicyUpdateState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyStatusRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyStatusIntent;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateStatus;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

/**
 * Answers the policy status of partition 1 to the broker's Hub receiver (ADR 0015): the committed
 * policy and the update that is open, if any. The receiver asks when it starts, and while it waits
 * for an update to be committed. The answer is read in log order, so it is exact.
 *
 * <p>Like a lookup, the query changes no state; it writes {@code QUERIED} so that the answer is
 * part of the log. Only partition 1 holds the policy state. The processor checks no user
 * authorization, like the other policy update processors; see {@link PolicyUpdateGuard}.
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class PolicyStatusQueryProcessor implements TypedRecordProcessor<PolicyStatusRecord> {

  private final PolicyState policyState;
  private final PolicyUpdateState policyUpdateState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public PolicyStatusQueryProcessor(
      final PolicyState policyState,
      final PolicyUpdateState policyUpdateState,
      final KeyGenerator keyGenerator,
      final Writers writers) {
    this.policyState = policyState;
    this.policyUpdateState = policyUpdateState;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<PolicyStatusRecord> command) {
    if (command.getPartitionId() != Protocol.DEPLOYMENT_PARTITION) {
      final var reason =
          "Expected the policy status to be queried on partition %d, but it was queried on partition %d"
              .formatted(Protocol.DEPLOYMENT_PARTITION, command.getPartitionId());
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
      responseWriter.writeRejectedResponseOnCommand(command, RejectionType.INVALID_STATE, reason);
      return;
    }

    final var status = new PolicyStatusRecord();
    policyState
        .getCurrentPolicy()
        .ifPresent(
            policy ->
                status
                    .setPolicyId(policy.policyId())
                    .setPolicyVersion(policy.policyVersion())
                    .setSeqNo(policy.seqNo()));
    policyUpdateState
        .getOpenUpdate()
        .ifPresent(
            update ->
                status
                    .setOpenUpdateId(update.getUpdate().getUpdateId())
                    .setUpdateStatus(
                        update.isApplying()
                            ? PolicyUpdateStatus.APPLYING
                            : PolicyUpdateStatus.PENDING));

    final long key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, PolicyStatusIntent.QUERIED, status);
    responseWriter.writeAcceptedResponseOnCommand(key, PolicyStatusIntent.QUERIED, status, command);
  }
}

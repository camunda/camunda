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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.PolicyUpdateState;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

/**
 * Cancels the open policy update while it still receives chunks, for example one that a previous
 * leader left open. Nothing of the update is applied yet, so its Hub event may be tried again under
 * a new update ID.
 *
 * <p>Once every chunk is buffered, the update is being applied, and nothing can undo the identity
 * commands it may have written. The command is then rejected, and the update runs to its end.
 *
 * <p>The processor checks no user authorization; see {@link PolicyUpdateGuard}.
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class PolicyUpdateCancelProcessor implements TypedRecordProcessor<PolicyUpdateRecord> {

  private final PolicyUpdateState policyUpdateState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;

  public PolicyUpdateCancelProcessor(
      final PolicyUpdateState policyUpdateState,
      final KeyGenerator keyGenerator,
      final Writers writers) {
    this.policyUpdateState = policyUpdateState;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<PolicyUpdateRecord> command) {
    final var rejection = PolicyUpdateGuard.checkInternal(command);
    if (rejection.isPresent()) {
      rejectionWriter.appendRejection(command, rejection.get().type(), rejection.get().reason());
      return;
    }

    final var updateId = command.getValue().getUpdateId();
    final var openUpdate =
        policyUpdateState
            .getOpenUpdate()
            .filter(open -> open.getUpdate().getUpdateId().equals(updateId));
    if (openUpdate.isEmpty()) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.NOT_FOUND,
          "Expected to cancel the open policy update '%s', but it is not open".formatted(updateId));
      return;
    }
    if (openUpdate.get().isApplying()) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_STATE,
          ("Expected to cancel policy update '%s' while it still receives chunks, but it is already"
                  + " being applied")
              .formatted(updateId));
      return;
    }

    final var canceled =
        PolicyUpdateRecords.withoutPolicyData(openUpdate.get().getUpdate(), 0)
            .setErrorMessage(command.getValue().getErrorMessage());
    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), PolicyUpdateIntent.CANCELED, canceled);
  }
}

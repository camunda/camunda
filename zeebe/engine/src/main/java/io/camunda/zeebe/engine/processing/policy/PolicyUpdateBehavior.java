/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.PolicyUpdateState;
import io.camunda.zeebe.engine.state.policy.PersistedPolicyUpdate;
import io.camunda.zeebe.protocol.impl.encoding.InternalOperationReference;
import io.camunda.zeebe.protocol.record.InternalOperationType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * Links the identity commands of a policy update to the update. {@link
 * PolicyUpdateApplyChunkProcessor} writes them with the update's key as internal operation
 * reference of type {@link InternalOperationType#POLICY_UPDATE}.
 *
 * <p>The identity processors use it to fail the update when one of its commands cannot be applied,
 * and to reject the commands that follow once the update has failed; see {@code
 * AbstractIdentityCommandProcessor}.
 *
 * <p>Distributed commands don't carry the reference, so this only works on the partition that
 * applies the update.
 */
@NullMarked
public final class PolicyUpdateBehavior {

  private final PolicyUpdateState policyUpdateState;
  private final StateWriter stateWriter;

  public PolicyUpdateBehavior(
      final PolicyUpdateState policyUpdateState, final StateWriter stateWriter) {
    this.policyUpdateState = policyUpdateState;
    this.stateWriter = stateWriter;
  }

  /** The metadata for an identity command of the update. */
  static FollowUpCommandMetadata metadataFor(final PersistedPolicyUpdate update) {
    return FollowUpCommandMetadata.of(
        builder ->
            builder.internalOperationReference(
                InternalOperationReference.policyUpdate(update.getUpdateKey())));
  }

  /** Whether the identity command was written for a policy update. */
  public static boolean isPartOfPolicyUpdate(final TypedRecord<?> command) {
    return InternalOperationReference.of(command).type() == InternalOperationType.POLICY_UPDATE;
  }

  /**
   * Rejects an identity command of a policy update that is no longer open. The update failed when
   * an earlier identity command of it could not be applied, and its remaining identity commands
   * must not change the identity state.
   */
  public Optional<Rejection> checkUpdateIsOpen(final TypedRecord<?> command) {
    if (!isPartOfPolicyUpdate(command) || findOpenUpdate(command).isPresent()) {
      return Optional.empty();
    }
    return Optional.of(
        new Rejection(
            RejectionType.INVALID_STATE,
            ("Expected policy update with key '%d' to be open when its command %s.%s is processed,"
                    + " but it failed or no longer exists")
                .formatted(
                    command.getInternalOperationReference(),
                    command.getValueType(),
                    command.getIntent())));
  }

  /**
   * Fails the open update if the identity command belongs to it, because the command could not be
   * applied. Failing the update removes it, so {@link #checkUpdateIsOpen} rejects its remaining
   * identity commands.
   *
   * @param cause why the command could not be applied, e.g. its rejection
   */
  public void failOpenUpdate(final TypedRecord<?> command, final String cause) {
    findOpenUpdate(command)
        .ifPresent(
            update -> {
              final var failed =
                  PolicyUpdateRecords.withoutPolicyData(update.getUpdate(), -1)
                      .setErrorMessage(
                          "Expected command %s.%s of policy update '%s' to be applied, but it %s"
                              .formatted(
                                  command.getValueType(),
                                  command.getIntent(),
                                  update.getUpdate().getUpdateId(),
                                  cause));
              stateWriter.appendFollowUpEvent(
                  update.getUpdateKey(), PolicyUpdateIntent.FAILED, failed);
            });
  }

  private Optional<PersistedPolicyUpdate> findOpenUpdate(final TypedRecord<?> command) {
    if (!isPartOfPolicyUpdate(command)) {
      return Optional.empty();
    }
    return policyUpdateState
        .getOpenUpdate()
        .filter(update -> update.getUpdateKey() == command.getInternalOperationReference());
  }
}

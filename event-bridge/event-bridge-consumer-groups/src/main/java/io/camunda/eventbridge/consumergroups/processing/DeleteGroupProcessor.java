/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the internal {@code DELETE_GROUP} command the retention task appends once a group has
 * been {@code EMPTY} long enough. {@link TransitionValidator#validateDelete} decides whether the
 * group is still deletable (it exists and is still empty — a member may have rejoined since the
 * retention task observed it); on success this emits {@code GROUP_DELETED} ({@code
 * GroupDeletedApplier} removes the group + offsets), otherwise a {@code COMMAND_REJECTION} with the
 * reason (no reply — internal command).
 */
public final class DeleteGroupProcessor implements TypedRecordProcessor<MembershipRecord> {

  private final Writers writers;
  private final TransitionValidator validator;

  public DeleteGroupProcessor(final Writers writers, final TransitionValidator validator) {
    this.writers = writers;
    this.validator = validator;
  }

  @Override
  public void processRecord(final TypedRecord<MembershipRecord> command) {
    validator
        .validateDelete(command.getValue())
        .ifRightOrLeft(ok -> delete(command), reason -> reject(command, reason));
  }

  private void delete(final TypedRecord<MembershipRecord> command) {
    writers
        .state()
        .appendFollowUpEvent(command.getKey(), CoordinatorIntent.GROUP_DELETED, command.getValue());
  }

  private void reject(final TypedRecord<MembershipRecord> command, final String reason) {
    writers.rejection().appendRejection(command, RejectionType.INVALID_STATE, reason);
  }
}

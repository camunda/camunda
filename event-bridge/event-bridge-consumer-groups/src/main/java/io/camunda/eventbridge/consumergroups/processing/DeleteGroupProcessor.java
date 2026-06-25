/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.state.group.GroupLifecycle;
import io.camunda.eventbridge.consumergroups.state.immutable.ConsumerGroupState;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the internal {@code DELETE_GROUP} command the retention task appends once a group has
 * been {@code EMPTY} long enough. It emits {@code GROUP_DELETED} when the group is still empty,
 * otherwise a rejection (no reply — internal command). Dropped when the group no longer exists or
 * is no longer empty (a member rejoined since the retention task observed it).
 */
public final class DeleteGroupProcessor implements TypedRecordProcessor<MembershipRecord> {

  private final Writers writers;
  private final ConsumerGroupState state;

  public DeleteGroupProcessor(final Writers writers, final ConsumerGroupState state) {
    this.writers = writers;
    this.state = state;
  }

  @Override
  public void processRecord(final TypedRecord<MembershipRecord> command) {
    final var groupId = command.getValue().getGroupId();
    final var group = state.getGroup(groupId);
    if (group == null) {
      reject(command, "group no longer exists");
      return;
    }
    if (group.getState() != GroupLifecycle.EMPTY || !state.isGroupEmpty(groupId)) {
      reject(command, "group '%s' is no longer empty".formatted(groupId));
      return;
    }
    writers
        .state()
        .appendFollowUpEvent(command.getKey(), CoordinatorIntent.GROUP_DELETED, command.getValue());
  }

  private void reject(final TypedRecord<MembershipRecord> command, final String reason) {
    writers.rejection().appendRejection(command, RejectionType.INVALID_STATE, reason);
  }
}

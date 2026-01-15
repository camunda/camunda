/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.appint.exporter.mapper;

import io.camunda.appint.exporter.event.Event;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.util.Set;

public class SupportedRecordsMapper implements RecordMapper<Event> {

  private final Set<Intent> intents = Set.of(UserTaskIntent.CREATED);

  @Override
  public boolean supports(final Record<?> record) {
    return intents.contains(record.getIntent());
  }

  @Override
  public Event map(final Record<?> record) {
    return switch (record.getIntent()) {
      case UserTaskIntent.CREATED -> mapUserTaskCreatedEvent((Record<UserTaskRecordValue>) record);
      default ->
          throw new IllegalArgumentException("Unsupported record intent: " + record.getIntent());
    };
  }

  private Event.UserTaskCreatedEvent mapUserTaskCreatedEvent(
      final Record<UserTaskRecordValue> record) {
    final var eventMetaData =
        new Event.EventMetaData(
            String.valueOf(record.getKey()),
            record.getIntent().name(),
            record.getValueType().name());

    final var processMetaData =
        new Event.ProcessMetaData(
            String.valueOf(record.getValue().getProcessDefinitionKey()),
            record.getValue().getBpmnProcessId(),
            String.valueOf(record.getValue().getProcessDefinitionVersion()),
            String.valueOf(record.getValue().getProcessInstanceKey()),
            record.getValue().getElementId(),
            String.valueOf(record.getValue().getElementInstanceKey()),
            record.getValue().getTenantId());

    final var userTaskMetaData =
        new Event.UserTaskMetaData(
            String.valueOf(record.getValue().getUserTaskKey()),
            record.getValue().getTags(),
            record.getValue().getAssignee(),
            record.getValue().getCandidateGroupsList(),
            record.getValue().getCandidateUsersList(),
            record.getValue().getExternalFormReference(),
            record.getValue().getPriority(),
            String.valueOf(record.getValue().getFormKey()));

    return new Event.UserTaskCreatedEvent(eventMetaData, userTaskMetaData, processMetaData);
  }
}

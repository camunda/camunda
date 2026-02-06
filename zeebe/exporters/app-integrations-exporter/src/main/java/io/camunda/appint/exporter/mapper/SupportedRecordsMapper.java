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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

public class SupportedRecordsMapper implements RecordMapper<Event> {

  private final Set<Intent> intents =
      Set.of(
          UserTaskIntent.CREATED,
          UserTaskIntent.ASSIGNED,
          UserTaskIntent.COMPLETED,
          UserTaskIntent.CANCELED);

  @Override
  public boolean supports(final Record<?> record) {
    return intents.contains(record.getIntent());
  }

  @Override
  public Event map(final Record<?> record) {
    return switch (record.getIntent()) {
      case UserTaskIntent.CREATED,
          UserTaskIntent.ASSIGNED,
          UserTaskIntent.COMPLETED,
          UserTaskIntent.CANCELED ->
          mapUserTaskCreatedEvent((Record<UserTaskRecordValue>) record);
      default ->
          throw new IllegalArgumentException("Unsupported record intent: " + record.getIntent());
    };
  }

  private Event.UserTaskEvent mapUserTaskCreatedEvent(final Record<UserTaskRecordValue> record) {
    final var eventMetaData = eventMetaDataFrom(record);
    final var processMetaData = erocessMetaDataFrom(record);
    final var userTaskMetaData = userTaskMetaDataFrom(record);
    return new Event.UserTaskEvent(eventMetaData, userTaskMetaData, processMetaData);
  }

  private static Event.EventMetaData eventMetaDataFrom(final Record<?> record) {
    return new Event.EventMetaData(
        String.valueOf(record.getKey()), record.getIntent().name(), record.getValueType().name());
  }

  private static Event.ProcessMetaData erocessMetaDataFrom(
      final Record<UserTaskRecordValue> record) {
    return new Event.ProcessMetaData(
        String.valueOf(record.getValue().getProcessDefinitionKey()),
        record.getValue().getBpmnProcessId(),
        String.valueOf(record.getValue().getProcessDefinitionVersion()),
        String.valueOf(record.getValue().getProcessInstanceKey()),
        record.getValue().getElementId(),
        String.valueOf(record.getValue().getElementInstanceKey()),
        record.getValue().getTenantId());
  }

  private static OffsetDateTime fromTimestamp(final long timestamp) {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC);
  }

  private static Event.UserTaskMetaData userTaskMetaDataFrom(
      final Record<UserTaskRecordValue> record) {
    final var createdAt = fromTimestamp(record.getValue().getCreationTimestamp());

    return new Event.UserTaskMetaData(
        String.valueOf(record.getValue().getUserTaskKey()),
        record.getValue().getTags(),
        record.getValue().getAssignee(),
        record.getValue().getCandidateGroupsList(),
        record.getValue().getCandidateUsersList(),
        record.getValue().getExternalFormReference(),
        record.getValue().getPriority(),
        String.valueOf(record.getValue().getFormKey()),
        createdAt);
  }
}

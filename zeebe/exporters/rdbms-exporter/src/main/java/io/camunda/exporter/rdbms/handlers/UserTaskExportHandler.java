/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.CANCELED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.CREATED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.MIGRATED;

import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel.UserTaskState;
import io.camunda.db.rdbms.write.service.UserTaskWriter;
import io.camunda.exporter.rdbms.DateUtil;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.OffsetDateTime;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * Based on UserTaskRecordToTaskEntityMapper
 */
public class UserTaskExportHandler implements RdbmsExportHandler<UserTaskRecordValue> {

  private static final Set<UserTaskIntent> EXPORTABLE_INTENTS =
      Set.of(
          UserTaskIntent.CREATED,
          UserTaskIntent.UPDATED,
          UserTaskIntent.CANCELED,
          UserTaskIntent.ASSIGNED,
          UserTaskIntent.COMPLETED,
          UserTaskIntent.MIGRATED);

  private final UserTaskWriter userTaskWriter;

  public UserTaskExportHandler(final UserTaskWriter userTaskWriter) {
    this.userTaskWriter = userTaskWriter;
  }

  @Override
  public boolean canExport(final Record<UserTaskRecordValue> record) {
    if (record.getIntent() != null && record.getIntent() instanceof final UserTaskIntent intent) {
      return EXPORTABLE_INTENTS.contains(intent);
    }

    return false;
  }

  @Override
  public void export(final Record<UserTaskRecordValue> record) {
    final UserTaskRecordValue value = record.getValue();
    switch (record.getIntent()) {
      case CREATED -> userTaskWriter.create(map(value, UserTaskState.CREATED, null));
      case CANCELED -> userTaskWriter.update(
          map(value, UserTaskState.CANCELED, DateUtil.toOffsetDateTime(record.getTimestamp())));
      case COMPLETED -> userTaskWriter.update(
          map(
              value,
              UserTaskState.COMPLETED,
              DateUtil.toOffsetDateTime(record.getTimestamp())));
      case MIGRATED -> userTaskWriter.migrateToProcess(
          value.getUserTaskKey(),
          value.getProcessDefinitionKey(),
          value.getBpmnProcessId(),
          value.getProcessDefinitionVersion(),
          value.getElementId());
      default -> userTaskWriter.update(map(value, null, null));
    }
  }

  private UserTaskDbModel map(
      final UserTaskRecordValue record,
      final UserTaskState state,
      final OffsetDateTime completionTime) {
    return new UserTaskDbModel.Builder()
        .userTaskKey(record.getUserTaskKey())
        .elementId(record.getElementId())
        .processDefinitionId(record.getBpmnProcessId())
        .creationDate(DateUtil.toOffsetDateTime(record.getCreationTimestamp()))
        .completionDate(completionTime)
        .assignee(StringUtils.isNotEmpty(record.getAssignee()) ? record.getAssignee() : null)
        .state(state)
        .formKey(record.getFormKey() > 0 ? record.getFormKey() : null)
        .processDefinitionKey(record.getProcessDefinitionKey())
        .processInstanceKey(record.getProcessInstanceKey())
        .elementInstanceKey(record.getElementInstanceKey())
        .tenantId(record.getTenantId())
        .dueDate(DateUtil.toOffsetDateTime(record.getDueDate()))
        .followUpDate(DateUtil.toOffsetDateTime(record.getFollowUpDate()))
        .candidateGroups(record.getCandidateGroupsList())
        .candidateUsers(record.getCandidateUsersList())
        .externalFormReference(
            StringUtils.isNotEmpty(record.getExternalFormReference())
                ? record.getExternalFormReference()
                : null)
        .processDefinitionVersion(record.getProcessDefinitionVersion())
        .customHeaders(record.getCustomHeaders())
        .priority(record.getPriority())
        .build();
  }
}

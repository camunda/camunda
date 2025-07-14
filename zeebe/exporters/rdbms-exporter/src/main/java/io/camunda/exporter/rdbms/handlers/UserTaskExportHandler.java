/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.ASSIGNED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.CANCELED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.CREATED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.MIGRATED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.UPDATED;

import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel.UserTaskState;
import io.camunda.db.rdbms.write.domain.UserTaskMigrationDbModel;
import io.camunda.db.rdbms.write.service.UserTaskWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.exporter.rdbms.utils.DateUtil;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.common.utils.ProcessCacheUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.OffsetDateTime;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/** Based on UserTaskRecordToTaskEntityMapper */
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
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;

  public UserTaskExportHandler(
      final UserTaskWriter userTaskWriter,
      final ExporterEntityCache<Long, CachedProcessEntity> processCache) {
    this.userTaskWriter = userTaskWriter;
    this.processCache = processCache;
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
      case CREATED -> userTaskWriter.create(map(record, UserTaskState.CREATED, null));
      case ASSIGNED, UPDATED -> userTaskWriter.update(map(record, UserTaskState.CREATED, null));
      case CANCELED ->
          userTaskWriter.update(
              map(
                  record,
                  UserTaskState.CANCELED,
                  DateUtil.toOffsetDateTime(record.getTimestamp())));
      case COMPLETED ->
          userTaskWriter.update(
              map(
                  record,
                  UserTaskState.COMPLETED,
                  DateUtil.toOffsetDateTime(record.getTimestamp())));
      case MIGRATED ->
          userTaskWriter.migrateToProcess(
              new UserTaskMigrationDbModel.Builder()
                  .userTaskKey(value.getUserTaskKey())
                  .processDefinitionKey(value.getProcessDefinitionKey())
                  .processDefinitionId(value.getBpmnProcessId())
                  .elementId(value.getElementId())
                  .name(
                      ProcessCacheUtil.getFlowNodeName(
                              processCache, value.getProcessDefinitionKey(), value.getElementId())
                          .orElse(null))
                  .processDefinitionVersion(value.getProcessDefinitionVersion())
                  .build());
      default -> {
        // All currently supported intents are handled explicitly above.
        // If new intent is added to EXPORTABLE_INTENTS but not handled here,
        // this default case ensures it is ignored until explicitly supported.
      }
    }
  }

  private UserTaskDbModel map(
      final Record<UserTaskRecordValue> record,
      final UserTaskState state,
      final OffsetDateTime completionTime) {
    final UserTaskRecordValue value = record.getValue();
    return new UserTaskDbModel.Builder()
        .userTaskKey(value.getUserTaskKey())
        .elementId(value.getElementId())
        .name(
            ProcessCacheUtil.getFlowNodeName(
                    processCache, value.getProcessDefinitionKey(), value.getElementId())
                .orElse(null))
        .processDefinitionId(value.getBpmnProcessId())
        .creationDate(DateUtil.toOffsetDateTime(value.getCreationTimestamp()))
        .completionDate(completionTime)
        .assignee(StringUtils.isNotEmpty(value.getAssignee()) ? value.getAssignee() : null)
        .state(state)
        .formKey(value.getFormKey() > 0 ? value.getFormKey() : null)
        .processDefinitionKey(value.getProcessDefinitionKey())
        .processInstanceKey(value.getProcessInstanceKey())
        .elementInstanceKey(value.getElementInstanceKey())
        .tenantId(value.getTenantId())
        .dueDate(DateUtil.toOffsetDateTime(value.getDueDate()))
        .followUpDate(DateUtil.toOffsetDateTime(value.getFollowUpDate()))
        .candidateGroups(value.getCandidateGroupsList())
        .candidateUsers(value.getCandidateUsersList())
        .externalFormReference(
            StringUtils.isNotEmpty(value.getExternalFormReference())
                ? value.getExternalFormReference()
                : null)
        .processDefinitionVersion(value.getProcessDefinitionVersion())
        .customHeaders(value.getCustomHeaders())
        .priority(value.getPriority())
        .partitionId(record.getPartitionId())
        .build();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.ASSIGNED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.CLAIMING;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.COMPLETION_DENIED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.UPDATED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.UPDATE_DENIED;

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
import java.util.EnumSet;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Based on UserTaskRecordToTaskEntityMapper */
public class UserTaskExportHandler implements RdbmsExportHandler<UserTaskRecordValue> {

  private static final Logger LOG = LoggerFactory.getLogger(UserTaskExportHandler.class);

  private static final Set<UserTaskIntent> EXPORTABLE_INTENTS =
      EnumSet.of(
          UserTaskIntent.CREATING,
          UserTaskIntent.CREATED,
          UserTaskIntent.ASSIGNING,
          UserTaskIntent.CLAIMING,
          UserTaskIntent.ASSIGNED,
          UserTaskIntent.UPDATING,
          UserTaskIntent.UPDATED,
          UserTaskIntent.COMPLETING,
          UserTaskIntent.COMPLETED,
          UserTaskIntent.CANCELING,
          UserTaskIntent.CANCELED,
          UserTaskIntent.MIGRATED,
          UserTaskIntent.ASSIGNMENT_DENIED,
          UserTaskIntent.UPDATE_DENIED,
          UserTaskIntent.COMPLETION_DENIED);

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
      case UserTaskIntent.CREATING ->
          userTaskWriter.create(
              map(record, UserTaskState.CREATING, null).toBuilder()
                  // Clear assignee as it shouldn't be persisted yet. While the CREATING event may
                  // contain it (if defined in the BPMN model), it's only used internally to
                  // trigger
                  // the assignment transition after the CREATED event. Externally, the task
                  // should
                  // remain unassigned until the ASSIGNED event is exported.
                  .assignee(null)
                  .build());
      case UserTaskIntent.ASSIGNING, CLAIMING ->
          userTaskWriter.updateState(value.getUserTaskKey(), UserTaskState.ASSIGNING);
      case UserTaskIntent.UPDATING ->
          userTaskWriter.updateState(value.getUserTaskKey(), UserTaskState.UPDATING);
      case UserTaskIntent.COMPLETING ->
          userTaskWriter.updateState(value.getUserTaskKey(), UserTaskState.COMPLETING);
      case UserTaskIntent.CANCELING ->
          userTaskWriter.updateState(value.getUserTaskKey(), UserTaskState.CANCELING);
      case UserTaskIntent.CREATED, ASSIGNED, UPDATED ->
          userTaskWriter.update(map(record, UserTaskState.CREATED, null));
      case UserTaskIntent.CANCELED ->
          userTaskWriter.update(
              map(
                  record,
                  UserTaskState.CANCELED,
                  DateUtil.toOffsetDateTime(record.getTimestamp())));
      case UserTaskIntent.COMPLETED ->
          userTaskWriter.update(
              map(
                  record,
                  UserTaskState.COMPLETED,
                  DateUtil.toOffsetDateTime(record.getTimestamp())));
      case UserTaskIntent.MIGRATED ->
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
      case UserTaskIntent.ASSIGNMENT_DENIED, UPDATE_DENIED, COMPLETION_DENIED ->
          userTaskWriter.updateState(value.getUserTaskKey(), UserTaskState.CREATED);
      default ->
          // All currently supported intents are handled explicitly above.
          // If new intent is added to EXPORTABLE_INTENTS but not handled here,
          // this default case ensures it is ignored until explicitly supported.
          LOG.warn("Unexpected intent {} for user task record", record.getIntent());
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
        .rootProcessInstanceKey(value.getRootProcessInstanceKey())
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
        .tags(value.getTags())
        .partitionId(record.getPartitionId())
        .build();
  }
}

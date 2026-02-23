/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.common.utils.ProcessCacheUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class UserTaskCreatingHandler implements ExportHandler<TaskEntity, UserTaskRecordValue> {

  private static final Set<UserTaskIntent> SUPPORTED_INTENTS = EnumSet.of(UserTaskIntent.CREATING);

  private final String indexName;
  private final ExporterEntityCache<String, CachedFormEntity> formCache;
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;
  private final ExporterMetadata exporterMetadata;

  public UserTaskCreatingHandler(
      final String indexName,
      final ExporterEntityCache<String, CachedFormEntity> formCache,
      final ExporterEntityCache<Long, CachedProcessEntity> processCache,
      final ExporterMetadata exporterMetadata) {
    this.indexName = indexName;
    this.formCache = formCache;
    this.processCache = processCache;
    this.exporterMetadata = exporterMetadata;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.USER_TASK;
  }

  @Override
  public Class<TaskEntity> getEntityType() {
    return TaskEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<UserTaskRecordValue> record) {
    return SUPPORTED_INTENTS.contains(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<UserTaskRecordValue> record) {
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK, record.getKey());
    if (refersToPreviousVersionRecord(record.getKey())) {
      return List.of(String.valueOf(record.getKey()));
    }
    return List.of(String.valueOf(record.getValue().getElementInstanceKey()));
  }

  @Override
  public TaskEntity createNewEntity(final String id) {
    return new TaskEntity().setId(id).setChangedAttributes(new ArrayList<>());
  }

  @Override
  public void updateEntity(final Record<UserTaskRecordValue> record, final TaskEntity entity) {
    createTaskEntity(entity, record);
  }

  @Override
  public void flush(final TaskEntity entity, final BatchRequest batchRequest) {
    final boolean previousVersionRecord = refersToPreviousVersionRecord(entity.getKey());

    batchRequest.addWithRouting(
        indexName,
        entity,
        previousVersionRecord ? String.valueOf(entity.getKey()) : entity.getProcessInstanceId());
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private void createTaskEntity(final TaskEntity entity, final Record<UserTaskRecordValue> record) {
    final var taskValue = record.getValue();
    final var formKey = taskValue.getFormKey() > 0 ? String.valueOf(taskValue.getFormKey()) : null;

    entity
        .setKey(record.getKey())
        .setImplementation(TaskImplementation.ZEEBE_USER_TASK)
        .setState(TaskState.CREATING)
        .setFlowNodeInstanceId(String.valueOf(taskValue.getElementInstanceKey()))
        .setProcessInstanceId(String.valueOf(taskValue.getProcessInstanceKey()))
        .setFlowNodeBpmnId(taskValue.getElementId())
        .setName(
            ProcessCacheUtil.getFlowNodeName(
                    processCache, taskValue.getProcessDefinitionKey(), taskValue.getElementId())
                .orElse(null))
        .setBpmnProcessId(taskValue.getBpmnProcessId())
        .setProcessDefinitionId(String.valueOf(taskValue.getProcessDefinitionKey()))
        .setProcessDefinitionVersion(taskValue.getProcessDefinitionVersion())
        .setFormKey(!ExporterUtil.isEmpty(formKey) ? formKey : null)
        .setExternalFormReference(
            ExporterUtil.isEmpty(taskValue.getExternalFormReference())
                ? null
                : taskValue.getExternalFormReference())
        .setCustomHeaders(taskValue.getCustomHeaders())
        .setPartitionId(record.getPartitionId())
        .setTenantId(taskValue.getTenantId())
        .setPosition(record.getPosition())
        .setAction(ExporterUtil.isEmpty(taskValue.getAction()) ? null : taskValue.getAction())
        .setCreationTime(
            ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
        .setDueDate(ExporterUtil.toOffsetDateTime(taskValue.getDueDate()))
        .setFollowUpDate(ExporterUtil.toOffsetDateTime(taskValue.getFollowUpDate()))
        .setPriority(taskValue.getPriority())
        .setCandidateGroups(ExporterUtil.toStringArrayOrNull(taskValue.getCandidateGroupsList()))
        .setCandidateUsers(ExporterUtil.toStringArrayOrNull(taskValue.getCandidateUsersList()));

    if (taskValue.getTags() != null && !taskValue.getTags().isEmpty()) {
      entity.setTags(taskValue.getTags());
    }

    if (!ExporterUtil.isEmpty(formKey)) {
      formCache
          .get(formKey)
          .ifPresent(c -> entity.setFormId(c.formId()).setFormVersion(c.formVersion()));
    }
    final long rootProcessInstanceKey = record.getValue().getRootProcessInstanceKey();
    if (rootProcessInstanceKey > 0) {
      entity.setRootProcessInstanceKey(rootProcessInstanceKey);
    }
    final TaskJoinRelationship joinRelation = new TaskJoinRelationship();
    joinRelation.setName(TaskJoinRelationshipType.TASK.getType());
    joinRelation.setParent(Long.parseLong(entity.getProcessInstanceId()));
    entity.setJoin(joinRelation);
  }

  private boolean refersToPreviousVersionRecord(final long key) {
    return exporterMetadata.getFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK) == -1
        || key < exporterMetadata.getFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK);
  }
}

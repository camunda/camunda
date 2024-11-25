/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.cache.ExporterEntityCache;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship;
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.tasklist.TaskState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTaskHandler implements ExportHandler<TaskEntity, UserTaskRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserTaskHandler.class);
  private static final Set<UserTaskIntent> SUPPORTED_INTENTS =
      EnumSet.of(
          UserTaskIntent.CREATED,
          UserTaskIntent.COMPLETED,
          UserTaskIntent.CANCELED,
          UserTaskIntent.MIGRATED,
          UserTaskIntent.ASSIGNED,
          UserTaskIntent.UPDATED);
  private final String indexName;
  private final ExporterEntityCache<String, CachedFormEntity> formCache;

  public UserTaskHandler(
      final String indexName, final ExporterEntityCache<String, CachedFormEntity> formCache) {
    this.indexName = indexName;
    this.formCache = formCache;
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
    return List.of(String.valueOf(record.getValue().getElementInstanceKey()));
  }

  @Override
  public TaskEntity createNewEntity(final String id) {
    return new TaskEntity().setId(id).setChangedAttributes(new ArrayList<>());
  }

  @Override
  public void updateEntity(final Record<UserTaskRecordValue> record, final TaskEntity entity) {
    entity.setProcessInstanceId(String.valueOf(record.getValue().getProcessInstanceKey()));
    entity.setAction(record.getValue().getAction());
    switch (record.getIntent()) {
      case UserTaskIntent.CREATED -> createTaskEntity(entity, record);
      case UserTaskIntent.ASSIGNED -> {
        entity.getChangedAttributes().add("assignee");
        if (ExporterUtil.isEmpty(record.getValue().getAssignee())) {
          entity.setAssignee(null);
        } else {
          entity.setAssignee(record.getValue().getAssignee());
        }
      }
      case UserTaskIntent.UPDATED -> {
        for (final String attribute : record.getValue().getChangedAttributes()) {
          entity.getChangedAttributes().add(attribute);
          switch (attribute) {
            case "candidateGroupsList" ->
                entity.setCandidateGroups(
                    record.getValue().getCandidateGroupsList().toArray(new String[0]));
            case "candidateUsersList" ->
                entity.setCandidateUsers(
                    record.getValue().getCandidateUsersList().toArray(new String[0]));
            case "dueDate" ->
                entity.setDueDate(ExporterUtil.toOffsetDateTime(record.getValue().getDueDate()));
            case "followUpDate" ->
                entity.setFollowUpDate(
                    ExporterUtil.toOffsetDateTime(record.getValue().getFollowUpDate()));
            case "priority" -> entity.setPriority(record.getValue().getPriority());
            default ->
                LOGGER.warn(
                    "Attribute update not mapped while importing ZEEBE_USER_TASKS: {}", attribute);
          }
        }
      }
      case UserTaskIntent.COMPLETED, UserTaskIntent.CANCELED ->
          entity
              .setState(
                  record.getIntent().equals(UserTaskIntent.COMPLETED)
                      ? TaskState.COMPLETED
                      : TaskState.CANCELED)
              .setCompletionTime(
                  ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
      case UserTaskIntent.MIGRATED ->
          entity
              .setFlowNodeBpmnId(record.getValue().getElementId())
              .setBpmnProcessId(record.getValue().getBpmnProcessId())
              .setProcessDefinitionId(String.valueOf(record.getValue().getProcessDefinitionKey()))
              .setState(TaskState.CREATED);
      default -> {}
    }

    final TaskJoinRelationship joinRelation = new TaskJoinRelationship();
    joinRelation.setName(TaskJoinRelationshipType.TASK.getType());
    joinRelation.setParent(Long.valueOf(entity.getProcessInstanceId()));
    entity.setJoin(joinRelation);
  }

  @Override
  public void flush(final TaskEntity entity, final BatchRequest batchRequest) {

    final Map<String, Object> updateFields = getUpdatedFields(entity);

    batchRequest.upsertWithRouting(
        indexName, entity.getId(), entity, updateFields, entity.getProcessInstanceId());
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private Map<String, Object> getUpdatedFields(final TaskEntity entity) {

    final Map<String, Object> updateFields = new HashMap<>();
    if (entity.getChangedAttributes() != null) {
      for (final String changedAttribute : entity.getChangedAttributes()) {
        switch (changedAttribute) {
          case "assignee" -> updateFields.put(TaskTemplate.ASSIGNEE, entity.getAssignee());
          case "candidateGroupsList" ->
              updateFields.put(TaskTemplate.CANDIDATE_GROUPS, entity.getCandidateGroups());
          case "candidateUsersList" ->
              updateFields.put(TaskTemplate.CANDIDATE_USERS, entity.getCandidateUsers());
          case "dueDate" -> updateFields.put(TaskTemplate.DUE_DATE, entity.getDueDate());
          case "followUpDate" ->
              updateFields.put(TaskTemplate.FOLLOW_UP_DATE, entity.getFollowUpDate());
          case "priority" -> updateFields.put(TaskTemplate.PRIORITY, entity.getPriority());
          default ->
              LOGGER.warn(
                  "Attribute update not mapped while importing ZEEBE_USER_TASKS: {}",
                  changedAttribute);
        }
      }
    }
    if (entity.getCompletionTime() != null) {
      updateFields.put(TaskTemplate.COMPLETION_TIME, entity.getCompletionTime());
    }

    if (entity.getChangedAttributes() != null && !entity.getChangedAttributes().isEmpty()) {
      updateFields.put(TaskTemplate.CHANGED_ATTRIBUTES, entity.getChangedAttributes());
    }
    if (entity.getAction() != null) {
      updateFields.put(TaskTemplate.ACTION, entity.getAction());
    }
    if (entity.getState() != null) {
      updateFields.put(TaskTemplate.STATE, entity.getState());
    }
    if (entity.getFlowNodeBpmnId() != null) {
      updateFields.put(TaskTemplate.FLOW_NODE_BPMN_ID, entity.getFlowNodeBpmnId());
    }
    if (entity.getProcessDefinitionId() != null) {
      updateFields.put(TaskTemplate.PROCESS_DEFINITION_ID, entity.getProcessDefinitionId());
    }
    if (entity.getBpmnProcessId() != null) {
      updateFields.put(TaskTemplate.BPMN_PROCESS_ID, entity.getBpmnProcessId());
    }

    return updateFields;
  }

  private void createTaskEntity(final TaskEntity entity, final Record<UserTaskRecordValue> record) {
    final var formKey =
        record.getValue().getFormKey() > 0 ? String.valueOf(record.getValue().getFormKey()) : null;

    entity
        .setImplementation(TaskImplementation.ZEEBE_USER_TASK)
        .setId(String.valueOf(record.getValue().getElementInstanceKey()))
        .setKey(record.getKey())
        .setState(TaskState.CREATED)
        .setAssignee(
            ExporterUtil.isEmpty(record.getValue().getAssignee())
                ? null
                : record.getValue().getAssignee())
        .setDueDate(ExporterUtil.toOffsetDateTime(record.getValue().getDueDate()))
        .setFollowUpDate(ExporterUtil.toOffsetDateTime(record.getValue().getFollowUpDate()))
        .setFlowNodeInstanceId(String.valueOf(record.getValue().getElementInstanceKey()))
        .setProcessInstanceId(String.valueOf(record.getValue().getProcessInstanceKey()))
        .setFlowNodeBpmnId(record.getValue().getElementId())
        .setBpmnProcessId(record.getValue().getBpmnProcessId())
        .setProcessDefinitionId(String.valueOf(record.getValue().getProcessDefinitionKey()))
        .setProcessDefinitionVersion(record.getValue().getProcessDefinitionVersion())
        .setFormKey(!ExporterUtil.isEmpty(formKey) ? formKey : null)
        .setExternalFormReference(
            ExporterUtil.isEmpty(record.getValue().getExternalFormReference())
                ? null
                : record.getValue().getExternalFormReference())
        .setCustomHeaders(record.getValue().getCustomHeaders())
        .setPriority(record.getValue().getPriority())
        .setPartitionId(record.getPartitionId())
        .setTenantId(record.getValue().getTenantId())
        .setPosition(record.getPosition())
        .setAction(
            ExporterUtil.isEmpty(record.getValue().getAction())
                ? null
                : record.getValue().getAction())
        .setCreationTime(
            ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));

    if (!record.getValue().getCandidateGroupsList().isEmpty()) {
      entity.setCandidateGroups(record.getValue().getCandidateGroupsList().toArray(new String[0]));
    }

    if (!record.getValue().getCandidateUsersList().isEmpty()) {
      entity.setCandidateUsers(record.getValue().getCandidateUsersList().toArray(new String[0]));
    }

    if (!ExporterUtil.isEmpty(formKey)) {
      formCache
          .get(formKey)
          .ifPresent(c -> entity.setFormId(c.formId()).setFormVersion(c.formVersion()));
    }
  }
}

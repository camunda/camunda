/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
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
          // CREATING intent is handled in UserTaskCreatingHandler to support completely overwriting
          // user task documents which is needed for user task process instance migration
          UserTaskIntent.CREATED,
          UserTaskIntent.COMPLETING,
          UserTaskIntent.COMPLETED,
          UserTaskIntent.CANCELING,
          UserTaskIntent.CANCELED,
          UserTaskIntent.MIGRATED,
          UserTaskIntent.ASSIGNING,
          UserTaskIntent.CLAIMING,
          UserTaskIntent.ASSIGNED,
          UserTaskIntent.UPDATING,
          UserTaskIntent.UPDATED,
          UserTaskIntent.ASSIGNMENT_DENIED,
          UserTaskIntent.UPDATE_DENIED,
          UserTaskIntent.COMPLETION_DENIED);
  private static final String UNMAPPED_USER_TASK_ATTRIBUTE_WARNING =
      "Attribute update not mapped while importing ZEEBE_USER_TASKS: {}";
  private final String indexName;
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;
  private final ExporterMetadata exporterMetadata;

  public UserTaskHandler(
      final String indexName,
      final ExporterEntityCache<Long, CachedProcessEntity> processCache,
      final ExporterMetadata exporterMetadata) {
    this.indexName = indexName;
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
    entity.setProcessInstanceId(String.valueOf(record.getValue().getProcessInstanceKey()));
    entity.setAction(record.getValue().getAction());
    entity.setKey(record.getKey());

    switch ((UserTaskIntent) record.getIntent()) {
      case UserTaskIntent.CREATED, UserTaskIntent.ASSIGNED, UserTaskIntent.UPDATED -> {
        entity.setState(TaskState.CREATED);
        entity.setCompletionTime(null);
        updateChangedAttributes(record, entity);
      }
      case UserTaskIntent.COMPLETED -> handleCompletion(record, entity);
      case UserTaskIntent.CANCELED -> handleCancellation(record, entity);
      case UserTaskIntent.MIGRATED -> handleMigration(record, entity);
      case UserTaskIntent.ASSIGNING, UserTaskIntent.CLAIMING ->
          entity.setState(TaskState.ASSIGNING);
      case UserTaskIntent.UPDATING -> entity.setState(TaskState.UPDATING);
      case UserTaskIntent.COMPLETING -> entity.setState(TaskState.COMPLETING);
      case UserTaskIntent.CANCELING -> entity.setState(TaskState.CANCELING);
      case UserTaskIntent.ASSIGNMENT_DENIED, UPDATE_DENIED, COMPLETION_DENIED ->
          entity.setState(TaskState.CREATED);
      default -> {}
    }

    final TaskJoinRelationship joinRelation = new TaskJoinRelationship();
    joinRelation.setName(TaskJoinRelationshipType.TASK.getType());
    joinRelation.setParent(Long.parseLong(entity.getProcessInstanceId()));
    entity.setJoin(joinRelation);
  }

  @Override
  public void flush(final TaskEntity entity, final BatchRequest batchRequest) {

    final Map<String, Object> updateFields = getUpdatedFields(entity);

    final boolean previousVersionRecord = refersToPreviousVersionRecord(entity.getKey());

    batchRequest.upsertWithRouting(
        indexName,
        previousVersionRecord ? String.valueOf(entity.getKey()) : entity.getId(),
        entity,
        updateFields,
        previousVersionRecord ? String.valueOf(entity.getKey()) : entity.getProcessInstanceId());
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
          default -> LOGGER.warn(UNMAPPED_USER_TASK_ATTRIBUTE_WARNING, changedAttribute);
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
    if (entity.getName() != null) {
      updateFields.put(TaskTemplate.NAME, entity.getName());
    }
    if (entity.getProcessDefinitionId() != null) {
      updateFields.put(TaskTemplate.PROCESS_DEFINITION_ID, entity.getProcessDefinitionId());
    }
    if (entity.getBpmnProcessId() != null) {
      updateFields.put(TaskTemplate.BPMN_PROCESS_ID, entity.getBpmnProcessId());
    }
    if (entity.getProcessDefinitionVersion() != null) {
      updateFields.put(
          TaskTemplate.PROCESS_DEFINITION_VERSION, entity.getProcessDefinitionVersion());
    }

    return updateFields;
  }

  private boolean refersToPreviousVersionRecord(final long key) {
    return exporterMetadata.getFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK) == -1
        || key < exporterMetadata.getFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK);
  }

  private static String getAssigneeOrNull(final Record<UserTaskRecordValue> record) {
    if (ExporterUtil.isEmpty(record.getValue().getAssignee())) {
      return null;
    }
    return record.getValue().getAssignee();
  }

  /**
   * Applies changes to the user task fields based on the attributes in the {@link
   * UserTaskRecordValue}.
   *
   * <p>This method can be used for updating fields either:
   *
   * <ul>
   *   <li>As a result of user task corrections configured while completing task listener jobs.
   *   <li>As a result of regular user task updates.
   * </ul>
   *
   * @param record the record containing user task data and changed attributes
   * @param entity the task entity to be updated
   */
  private void updateChangedAttributes(
      final Record<UserTaskRecordValue> record, final TaskEntity entity) {
    final var value = record.getValue();

    for (final String attribute : value.getChangedAttributes()) {
      entity.addChangedAttribute(attribute);

      switch (attribute) {
        case "assignee" -> entity.setAssignee(getAssigneeOrNull(record));
        case "candidateGroupsList" ->
            entity.setCandidateGroups(
                ExporterUtil.toStringArrayOrNull(value.getCandidateGroupsList()));
        case "candidateUsersList" ->
            entity.setCandidateUsers(
                ExporterUtil.toStringArrayOrNull(value.getCandidateUsersList()));
        case "dueDate" -> entity.setDueDate(ExporterUtil.toOffsetDateTime(value.getDueDate()));
        case "followUpDate" ->
            entity.setFollowUpDate(ExporterUtil.toOffsetDateTime(value.getFollowUpDate()));
        case "priority" -> entity.setPriority(value.getPriority());
        default -> LOGGER.warn(UNMAPPED_USER_TASK_ATTRIBUTE_WARNING, attribute);
      }
    }
  }

  private void handleCompletion(final Record<UserTaskRecordValue> record, final TaskEntity entity) {
    entity
        .setState(TaskState.COMPLETED)
        .setCompletionTime(
            ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    updateChangedAttributes(record, entity);
  }

  private void handleCancellation(
      final Record<UserTaskRecordValue> record, final TaskEntity entity) {
    entity
        .setState(TaskState.CANCELED)
        .setCompletionTime(
            ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    updateChangedAttributes(record, entity);
  }

  private void handleMigration(final Record<UserTaskRecordValue> record, final TaskEntity entity) {
    entity
        .setFlowNodeBpmnId(record.getValue().getElementId())
        .setName(
            ProcessCacheUtil.getFlowNodeName(
                    processCache,
                    record.getValue().getProcessDefinitionKey(),
                    record.getValue().getElementId())
                .orElse(null))
        .setBpmnProcessId(record.getValue().getBpmnProcessId())
        .setProcessDefinitionId(String.valueOf(record.getValue().getProcessDefinitionKey()))
        .setProcessDefinitionVersion(record.getValue().getProcessDefinitionVersion());
  }
}

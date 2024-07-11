/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.UserTaskEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.UserTaskTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTaskHandler implements ExportHandler<UserTaskEntity, UserTaskRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserTaskHandler.class);
  private static final Set<Intent> CREATE_STATES = Set.of(UserTaskIntent.CREATED);
  private static final Set<Intent> UPDATE_STATES =
      Set.of(
          UserTaskIntent.ASSIGNED,
          UserTaskIntent.MIGRATED,
          UserTaskIntent.UPDATED,
          UserTaskIntent.COMPLETED,
          UserTaskIntent.CANCELED);
  private final UserTaskTemplate userTaskTemplate;
  private final ObjectMapper objectMapper;

  public UserTaskHandler(final UserTaskTemplate userTaskTemplate, final ObjectMapper objectMapper) {
    this.userTaskTemplate = userTaskTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.USER_TASK;
  }

  @Override
  public Class<UserTaskEntity> getEntityType() {
    return UserTaskEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<UserTaskRecordValue> record) {
    final Intent intent = record.getIntent();
    return (CREATE_STATES.contains(intent) || UPDATE_STATES.contains(intent));
  }

  @Override
  public List<String> generateIds(final Record<UserTaskRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getUserTaskKey()));
  }

  @Override
  public UserTaskEntity createNewEntity(final String id) {
    return new UserTaskEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<UserTaskRecordValue> record, final UserTaskEntity entity) {
    final UserTaskRecordValue userTaskRecordValue = record.getValue();
    entity
        .setId(String.valueOf(userTaskRecordValue.getUserTaskKey()))
        .setKey(userTaskRecordValue.getUserTaskKey())
        .setUserTaskKey(userTaskRecordValue.getUserTaskKey())
        .setPartitionId(record.getPartitionId())
        .setBpmnProcessId(userTaskRecordValue.getBpmnProcessId())
        .setTenantId(userTaskRecordValue.getTenantId())
        .setProcessInstanceKey(userTaskRecordValue.getProcessInstanceKey())
        .setAssignee(userTaskRecordValue.getAssignee())
        .setCandidateGroups(userTaskRecordValue.getCandidateGroupsList())
        .setCandidateUsers(userTaskRecordValue.getCandidateUsersList())
        .setDueDate(toDateOrNull(userTaskRecordValue.getDueDate()))
        .setFollowUpDate(toDateOrNull(userTaskRecordValue.getFollowUpDate()))
        .setElementId(userTaskRecordValue.getElementId())
        .setElementInstanceKey(userTaskRecordValue.getElementInstanceKey())
        .setProcessDefinitionKey(userTaskRecordValue.getProcessDefinitionKey())
        .setProcessDefinitionVersion(userTaskRecordValue.getProcessDefinitionVersion())
        .setVariables(getVariablesAsJsonString(userTaskRecordValue))
        .setFormKey(userTaskRecordValue.getFormKey())
        .setChangedAttributes(userTaskRecordValue.getChangedAttributes())
        .setAction(userTaskRecordValue.getAction());
  }

  @Override
  public void flush(final UserTaskEntity entity, final NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {
    // Original logic used intent field from record to determine which fields to update. That is
    // not available in this function, so upsert must be called with all fields as possible
    // updated fields

    final Map<String, Object> upsertUpdateFields = new HashMap<>();
    upsertUpdateFields.put(UserTaskTemplate.KEY, entity.getKey());
    upsertUpdateFields.put(UserTaskTemplate.USER_TASK_KEY, entity.getUserTaskKey());
    upsertUpdateFields.put(UserTaskTemplate.PARTITION_ID, entity.getPartitionId());
    upsertUpdateFields.put(UserTaskTemplate.BPMN_PROCESS_ID, entity.getBpmnProcessId());
    upsertUpdateFields.put(UserTaskTemplate.TENANT_ID, entity.getTenantId());
    upsertUpdateFields.put(UserTaskTemplate.PROCESS_INSTANCE_KEY, entity.getProcessInstanceKey());
    upsertUpdateFields.put(UserTaskTemplate.ASSIGNEE, entity.getAssignee());
    upsertUpdateFields.put(UserTaskTemplate.CANDIDATE_GROUPS, entity.getCandidateGroups());
    upsertUpdateFields.put(UserTaskTemplate.CANDIDATE_USERS, entity.getCandidateUsers());
    upsertUpdateFields.put(UserTaskTemplate.DUE_DATE, entity.getDueDate());
    upsertUpdateFields.put(UserTaskTemplate.FOLLOW_UP_DATE, entity.getFollowUpDate());
    upsertUpdateFields.put(UserTaskTemplate.ELEMENT_ID, entity.getElementId());
    upsertUpdateFields.put(UserTaskTemplate.ELEMENT_INSTANCE_KEY, entity.getElementInstanceKey());
    upsertUpdateFields.put(
        UserTaskTemplate.PROCESS_DEFINITION_KEY, entity.getProcessDefinitionKey());
    upsertUpdateFields.put(
        UserTaskTemplate.PROCESS_DEFINITION_VERSION, entity.getProcessDefinitionVersion());
    upsertUpdateFields.put(UserTaskTemplate.VARIABLES, entity.getVariables());
    upsertUpdateFields.put(UserTaskTemplate.FORM_KEY, entity.getFormKey());
    upsertUpdateFields.put(UserTaskTemplate.CHANGED_ATTRIBUTES, entity.getChangedAttributes());
    upsertUpdateFields.put(UserTaskTemplate.ACTION, entity.getAction());

    batchRequest.upsert(
        userTaskTemplate.getFullQualifiedName(), entity.getId(), entity, upsertUpdateFields);
  }

  @Override
  public String getIndexName() {
    return userTaskTemplate.getFullQualifiedName();
  }

  private OffsetDateTime toDateOrNull(final String dateString) {
    if (dateString == null) {
      return null;
    }
    try {
      return OffsetDateTime.parse(dateString);
    } catch (final Exception e) {
      LOGGER.warn("Could not parse {} as OffsetDateTime. Use null.", dateString);
      return null;
    }
  }

  private String getVariablesAsJsonString(final UserTaskRecordValue userTaskRecordValue) {
    try {
      return objectMapper.writeValueAsString(userTaskRecordValue.getVariables());
    } catch (final JsonProcessingException e) {
      LOGGER.warn(
          "Error writing variables {} as json string, using null.",
          userTaskRecordValue.getVariables());
      return null;
    }
  }
}

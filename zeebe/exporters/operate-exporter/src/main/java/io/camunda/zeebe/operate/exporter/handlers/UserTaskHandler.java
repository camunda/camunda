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
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.UserTaskTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.Tuple;
import io.camunda.zeebe.operate.exporter.util.OperateExportUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
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

  private final Map<Intent, Function<UserTaskEntity, Map<String, Object>>> intentToUpdateFields =
      Map.of(
          UserTaskIntent.UPDATED, UserTaskHandler::getUpdateFields,
          UserTaskIntent.MIGRATED, UserTaskHandler::getMigratedFields,
          UserTaskIntent.ASSIGNED, UserTaskHandler::getAssignedFields,
          UserTaskIntent.COMPLETED, UserTaskHandler::getCompletedFields,
          UserTaskIntent.CANCELED, UserTaskHandler::getCanceledFields);

  private final Map<String, Record<UserTaskRecordValue>> recordsMap = new HashMap<>();

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
  public boolean handlesRecord(Record<UserTaskRecordValue> record) {
    final Intent intent = record.getIntent();
    return CREATE_STATES.contains(intent) || UPDATE_STATES.contains(intent);
  }

  @Override
  public List<String> generateIds(Record<UserTaskRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getUserTaskKey()));
  }

  @Override
  public UserTaskEntity createNewEntity(String id) {
    return new UserTaskEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<UserTaskRecordValue> record, UserTaskEntity entity) {
    final UserTaskRecordValue userTaskRecordValue = record.getValue();
    final String id = String.valueOf(userTaskRecordValue.getUserTaskKey());
    recordsMap.put(id, record);

    try {
      entity
          .setId(id)
          .setKey(userTaskRecordValue.getUserTaskKey())
          .setUserTaskKey(userTaskRecordValue.getUserTaskKey())
          .setPartitionId(record.getPartitionId())
          .setBpmnProcessId(userTaskRecordValue.getBpmnProcessId())
          .setTenantId(userTaskRecordValue.getTenantId())
          .setProcessInstanceKey(userTaskRecordValue.getProcessInstanceKey())
          .setAssignee(userTaskRecordValue.getAssignee())
          .setCandidateGroups(userTaskRecordValue.getCandidateGroupsList())
          .setCandidateUsers(userTaskRecordValue.getCandidateUsersList())
          .setDueDate(OperateExportUtil.toDateOrNull(userTaskRecordValue.getDueDate()))
          .setFollowUpDate(OperateExportUtil.toDateOrNull(userTaskRecordValue.getFollowUpDate()))
          .setElementId(userTaskRecordValue.getElementId())
          .setElementInstanceKey(userTaskRecordValue.getElementInstanceKey())
          .setProcessDefinitionKey(userTaskRecordValue.getProcessDefinitionKey())
          .setProcessDefinitionVersion(userTaskRecordValue.getProcessDefinitionVersion())
          .setVariables(objectMapper.writeValueAsString(userTaskRecordValue.getVariables()))
          .setFormKey(userTaskRecordValue.getFormKey())
          .setChangedAttributes(userTaskRecordValue.getChangedAttributes())
          .setAction(userTaskRecordValue.getAction());
    } catch (JsonProcessingException ex) {
      final String error =
          String.format(
              "Error while updating entity of type %s with id %s",
              entity.getClass().getSimpleName(), id);
      LOGGER.error(error, ex);
      throw new OperateRuntimeException(error, ex);
    }
  }

  @Override
  public void flush(UserTaskEntity entity, NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {

    final String id = entity.getId();
    final Record<UserTaskRecordValue> record = recordsMap.get(id);
    final Intent intent = (record == null) ? null : record.getIntent();
    if (intent == null) {
      LOGGER.warn("Intent is null for user task: id {}", id);
    }
    final Map<String, Object> updateFields = getUpdateFieldsMapByIntent(intent, entity);
    batchRequest.upsert(
        userTaskTemplate.getFullQualifiedName(), entity.getId(), entity, updateFields);
    LOGGER.debug(
        "Updated UserTaskEntity {} with update fields {} to batch request",
        entity.getId(),
        updateFields);
  }

  @Override
  public String getIndexName() {
    return userTaskTemplate.getFullQualifiedName();
  }

  private Map<String, Object> getUpdateFieldsMapByIntent(Intent intent, UserTaskEntity entity) {
    if (intent != null && intentToUpdateFields.containsKey(intent)) {
      return intentToUpdateFields.get(intent).apply(entity);
    }
    return new HashMap<>();
  }

  private static Map<String, Object> getCanceledFields(final UserTaskEntity userTaskEntity) {
    return Map.of(UserTaskTemplate.ACTION, userTaskEntity.getAction());
  }

  private static Map<String, Object> getCompletedFields(final UserTaskEntity userTaskEntity) {
    return Map.of(
        UserTaskTemplate.VARIABLES,
        userTaskEntity.getVariables(),
        UserTaskTemplate.ACTION,
        userTaskEntity.getAction());
  }

  private static Map<String, Object> getAssignedFields(final UserTaskEntity userTaskEntity) {
    return Map.of(
        UserTaskTemplate.ASSIGNEE,
        userTaskEntity.getAssignee(),
        UserTaskTemplate.ACTION,
        userTaskEntity.getAction());
  }

  private static Map<String, Object> getUpdateFields(final UserTaskEntity userTaskEntity) {
    final Map<String, Tuple<String, Supplier<Object>>> changedAttributesToUserEntitySupplier =
        Map.of(
            "candidateUserList",
            new Tuple<>(UserTaskTemplate.CANDIDATE_USERS, userTaskEntity::getCandidateUsers),
            "candidateUsersList",
            new Tuple<>(UserTaskTemplate.CANDIDATE_USERS, userTaskEntity::getCandidateUsers),
            "candidateGroupList",
            new Tuple<>(UserTaskTemplate.CANDIDATE_GROUPS, userTaskEntity::getCandidateGroups),
            "candidateGroupsList",
            new Tuple<>(UserTaskTemplate.CANDIDATE_GROUPS, userTaskEntity::getCandidateGroups),
            "dueDate",
            new Tuple<>(UserTaskTemplate.DUE_DATE, userTaskEntity::getDueDate),
            "followUpDate",
            new Tuple<>(UserTaskTemplate.FOLLOW_UP_DATE, userTaskEntity::getFollowUpDate));

    final var changedAttributes = userTaskEntity.getChangedAttributes();
    final var map = new HashMap<String, Object>();
    map.put(UserTaskTemplate.CHANGED_ATTRIBUTES, changedAttributes);
    map.put(UserTaskTemplate.ACTION, userTaskEntity.getAction());
    for (final var changedAttribute : changedAttributes) {
      final var fieldAndValueSupplier = changedAttributesToUserEntitySupplier.get(changedAttribute);
      if (fieldAndValueSupplier != null) {
        map.put(fieldAndValueSupplier.getLeft(), fieldAndValueSupplier.getRight().get());
      } else {
        LOGGER.warn(
            "Could not find attribute {} from changed attributes {}. This will be ignored.",
            changedAttribute,
            changedAttributes);
      }
    }
    return map;
  }

  private static Map<String, Object> getMigratedFields(final UserTaskEntity userTaskEntity) {
    return Map.of(
        UserTaskTemplate.BPMN_PROCESS_ID,
        userTaskEntity.getBpmnProcessId(),
        UserTaskTemplate.PROCESS_DEFINITION_VERSION,
        userTaskEntity.getProcessDefinitionVersion(),
        UserTaskTemplate.PROCESS_DEFINITION_KEY,
        userTaskEntity.getProcessDefinitionKey(),
        UserTaskTemplate.ELEMENT_ID,
        userTaskEntity.getElementId());
  }
}

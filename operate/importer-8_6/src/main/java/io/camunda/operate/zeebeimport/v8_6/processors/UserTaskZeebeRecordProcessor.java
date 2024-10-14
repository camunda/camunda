/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.v8_6.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.Tuple;
import io.camunda.webapps.schema.descriptors.operate.template.UserTaskTemplate;
import io.camunda.webapps.schema.entities.operate.UserTaskEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class UserTaskZeebeRecordProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserTaskZeebeRecordProcessor.class);
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

  private final Map<Intent, Function<UserTaskEntity, Map<String, Object>>> intentToUpdateFields =
      Map.of(
          UserTaskIntent.UPDATED,
          this::getUpdateFields,
          UserTaskIntent.MIGRATED,
          this::getMigratedFields,
          UserTaskIntent.ASSIGNED,
          this::getAssignedFields,
          UserTaskIntent.COMPLETED,
          this::getCompletedFields,
          UserTaskIntent.CANCELED,
          this::getCanceledFields);

  public UserTaskZeebeRecordProcessor(
      final UserTaskTemplate userTaskTemplate,
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper) {
    this.userTaskTemplate = userTaskTemplate;
    this.objectMapper = objectMapper;
  }

  private Map<String, Object> getCanceledFields(final UserTaskEntity userTaskEntity) {
    return Map.of(UserTaskTemplate.ACTION, userTaskEntity.getAction());
  }

  private Map<String, Object> getCompletedFields(final UserTaskEntity userTaskEntity) {
    return Map.of(
        UserTaskTemplate.VARIABLES,
        userTaskEntity.getVariables(),
        UserTaskTemplate.ACTION,
        userTaskEntity.getAction());
  }

  private Map<String, Object> getAssignedFields(final UserTaskEntity userTaskEntity) {
    return Map.of(
        UserTaskTemplate.ASSIGNEE,
        userTaskEntity.getAssignee(),
        UserTaskTemplate.ACTION,
        userTaskEntity.getAction());
  }

  private Map<String, Object> getUpdateFields(final UserTaskEntity userTaskEntity) {
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
            new Tuple<>(UserTaskTemplate.FOLLOW_UP_DATE, userTaskEntity::getFollowUpDate),
            "priority",
            new Tuple<>(UserTaskTemplate.PRIORITY, userTaskEntity::getPriority));

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

  private Map<String, Object> getMigratedFields(final UserTaskEntity userTaskEntity) {
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

  public void processUserTaskRecord(
      final BatchRequest batchRequest, final Record<UserTaskRecordValue> userTaskRecord)
      throws PersistenceException {
    final Intent intent = userTaskRecord.getIntent();
    LOGGER.info("Intent is: {}", intent);
    final var userTaskValue = userTaskRecord.getValue();
    final UserTaskEntity userTaskEntity;
    try {
      userTaskEntity = createEntity(userTaskRecord);
    } catch (final JsonProcessingException e) {
      throw new OperateRuntimeException(
          String.format("Could not create UserTaskEntity from record value %s", userTaskValue), e);
    }
    if (CREATE_STATES.contains(intent)) {
      persistUserTask(userTaskEntity, batchRequest);
    } else if (UPDATE_STATES.contains(intent)) {
      updateUserTask(
          userTaskEntity, intentToUpdateFields.get(intent).apply(userTaskEntity), batchRequest);
    } else {
      LOGGER.debug("UserTask record with intent {} is ignored", intent);
    }
  }

  private void updateUserTask(
      final UserTaskEntity userTaskEntity,
      final Map<String, Object> updateFields,
      final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.upsert(
        userTaskTemplate.getFullQualifiedName(),
        userTaskEntity.getId(),
        userTaskEntity,
        updateFields);
    LOGGER.debug(
        "Updated UserTaskEntity {} with update fields {} to batch request",
        userTaskEntity.getId(),
        updateFields);
  }

  private void persistUserTask(final UserTaskEntity userTaskEntity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.addWithId(
        userTaskTemplate.getFullQualifiedName(), userTaskEntity.getId(), userTaskEntity);
    LOGGER.debug("Added UserTaskEntity {} to batch request", userTaskEntity);
  }

  private UserTaskEntity createEntity(final Record<UserTaskRecordValue> userTaskRecord)
      throws JsonProcessingException {
    final UserTaskRecordValue userTaskRecordValue = userTaskRecord.getValue();
    return new UserTaskEntity()
        .setId(String.valueOf(userTaskRecordValue.getUserTaskKey()))
        .setKey(userTaskRecordValue.getUserTaskKey())
        .setUserTaskKey(userTaskRecordValue.getUserTaskKey())
        .setPartitionId(userTaskRecord.getPartitionId())
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
        .setVariables(objectMapper.writeValueAsString(userTaskRecordValue.getVariables()))
        .setFormKey(userTaskRecordValue.getFormKey())
        .setChangedAttributes(userTaskRecordValue.getChangedAttributes())
        .setAction(userTaskRecordValue.getAction())
        .setPriority(userTaskRecordValue.getPriority());
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
}

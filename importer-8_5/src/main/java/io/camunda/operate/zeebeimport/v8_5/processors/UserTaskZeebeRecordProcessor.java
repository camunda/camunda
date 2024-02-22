/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_5.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.UserTaskEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.UserTaskTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.OffsetDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserTaskZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(UserTaskZeebeRecordProcessor.class);
  private static final Set<String> CREATE_STATES = Set.of(UserTaskIntent.CREATED.name());
  private static final Set<String> UPDATE_STATES =
      Set.of(UserTaskIntent.ASSIGNED.name(), UserTaskIntent.MIGRATED.name());
  private final UserTaskTemplate userTaskTemplate;
  private final ObjectMapper objectMapper;

  public UserTaskZeebeRecordProcessor(
      final UserTaskTemplate userTaskTemplate, final ObjectMapper objectMapper) {
    this.userTaskTemplate = userTaskTemplate;
    this.objectMapper = objectMapper;
  }

  private static Map<String, Object> getUpdateFieldsMapByIntent(
      final String intent,
      final UserTaskRecordValue userTaskValue,
      final UserTaskEntity userTaskEntity) {
    final Map<String, Object> updateFields = new HashMap<>();
    if (intent.equals(UserTaskIntent.ASSIGNED.name())) {
      updateFields.put(UserTaskTemplate.ASSIGNEE, userTaskValue.getAssignee());
    }
    if (intent.equals(UserTaskIntent.MIGRATED.name())) {
      updateFields.put(UserTaskTemplate.BPMN_PROCESS_ID, userTaskEntity.getBpmnProcessId());
      updateFields.put(
          UserTaskTemplate.PROCESS_DEFINITION_VERSION,
          userTaskEntity.getProcessDefinitionVersion());
      updateFields.put(
          UserTaskTemplate.PROCESS_DEFINITION_KEY, userTaskEntity.getProcessDefinitionKey());
      updateFields.put(UserTaskTemplate.ELEMENT_ID, userTaskEntity.getElementId());
    }
    return updateFields;
  }

  public void processUserTaskRecord(
      final BatchRequest batchRequest, final Record<UserTaskRecordValue> userTaskRecord)
      throws PersistenceException {
    final String intent = userTaskRecord.getIntent().name();
    logger.info("Intent is: {}", intent);
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
      final Map<String, Object> updateFields =
          getUpdateFieldsMapByIntent(intent, userTaskValue, userTaskEntity);
      updateUserTask(userTaskEntity, updateFields, batchRequest);
    } else {
      logger.debug("UserTask record with intent {} is ignored", intent);
    }
  }

  private void updateUserTask(
      final UserTaskEntity userTaskEntity,
      final Map<String, Object> updateFields,
      final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.update(
        userTaskTemplate.getFullQualifiedName(), userTaskEntity.getId(), updateFields);
    logger.debug(
        "Updated UserTaskEntity {} with update fields {} to batch request",
        userTaskEntity.getId(),
        updateFields);
  }

  private void persistUserTask(final UserTaskEntity userTaskEntity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.addWithId(
        userTaskTemplate.getFullQualifiedName(), userTaskEntity.getId(), userTaskEntity);
    logger.debug("Added UserTaskEntity {} to batch request", userTaskEntity);
  }

  private UserTaskEntity createEntity(final Record<UserTaskRecordValue> userTaskRecord)
      throws JsonProcessingException {
    final UserTaskRecordValue userTaskRecordValue = userTaskRecord.getValue();
    return new UserTaskEntity()
        .setId(String.valueOf(userTaskRecordValue.getUserTaskKey()))
        .setKey(userTaskRecordValue.getUserTaskKey())
        .setPartitionId(userTaskRecord.getPartitionId())
        .setBpmnProcessId(userTaskRecordValue.getBpmnProcessId())
        .setTenantId(userTaskRecordValue.getTenantId())
        .setProcessInstanceKey(userTaskRecordValue.getProcessInstanceKey())
        .setBpmnProcessId(userTaskRecordValue.getBpmnProcessId())
        .setAssignee(userTaskRecordValue.getAssignee())
        .setCandidateGroups(List.of(userTaskRecordValue.getCandidateGroups()))
        .setCandidateUsers(List.of(userTaskRecordValue.getCandidateUsers()))
        .setDueDate(toDateOrNull(userTaskRecordValue.getDueDate()))
        .setElementId(userTaskRecordValue.getElementId())
        .setElementInstanceKey(userTaskRecordValue.getElementInstanceKey())
        .setProcessDefinitionKey(userTaskRecordValue.getProcessDefinitionKey())
        .setUserTaskKey(userTaskRecordValue.getUserTaskKey())
        .setVariables(objectMapper.writeValueAsString(userTaskRecordValue.getVariables()))
        .setFollowUpDate(toDateOrNull(userTaskRecordValue.getFollowUpDate()))
        .setFormKey(userTaskRecordValue.getFormKey());
  }

  private OffsetDateTime toDateOrNull(final String dateString) {
    if (dateString == null) return null;
    try {
      return OffsetDateTime.parse(dateString);
    } catch (final Exception e) {
      logger.warn("Could not parse {} as OffsetDateTime. Use null.", dateString);
      return null;
    }
  }
}

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class UserTaskZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(UserTaskZeebeRecordProcessor.class);

  private final UserTaskTemplate userTaskTemplate;

  private final ObjectMapper objectMapper;

  public UserTaskZeebeRecordProcessor(final UserTaskTemplate userTaskTemplate, final ObjectMapper objectMapper){
    this.userTaskTemplate = userTaskTemplate;
    this.objectMapper = objectMapper;
  }

  private static final Set<String> STATES = new HashSet<>();
  static {
    STATES.add(UserTaskIntent.CREATED.name());
  }

  public void processUserTaskRecord(BatchRequest batchRequest, Record<UserTaskRecordValue> userTaskRecord) throws PersistenceException {
    final String intent = userTaskRecord.getIntent().name();

    if (STATES.contains(intent)) {
      UserTaskRecordValue recordValue = userTaskRecord.getValue();
      persistUserTask(recordValue, batchRequest);
    }
  }

  private void persistUserTask(UserTaskRecordValue recordValue, BatchRequest batchRequest) throws PersistenceException {
    UserTaskEntity userTaskEntity;
    try {
      userTaskEntity = createEntity(recordValue);
    } catch (JsonProcessingException e) {
      throw new OperateRuntimeException(String.format("Could not create UserTaskEntity from record value %s", recordValue), e);
    }
    batchRequest.addWithId(userTaskTemplate.getFullQualifiedName(), userTaskEntity.getId(), userTaskEntity);
    logger.debug("Added UserTaskEntity {} to batch request", userTaskEntity);
  }

  private UserTaskEntity createEntity(UserTaskRecordValue userTaskRecord) throws JsonProcessingException {
    return new UserTaskEntity()
        .setId(String.valueOf(userTaskRecord.getUserTaskKey()))
        .setBpmnProcessId(userTaskRecord.getBpmnProcessId())
        .setTenantId(userTaskRecord.getTenantId())
        .setProcessInstanceKey(userTaskRecord.getProcessInstanceKey())
        .setBpmnProcessId(userTaskRecord.getBpmnProcessId())
        .setAssignee(userTaskRecord.getAssignee())
        .setCandidateGroups(List.of(userTaskRecord.getCandidateGroups()))
        .setCandidateUsers(List.of(userTaskRecord.getCandidateUsers()))
        .setDueDate(toDateOrNull(userTaskRecord.getDueDate()))
        .setElementId(userTaskRecord.getElementId())
        .setElementInstanceKey(userTaskRecord.getElementInstanceKey())
        .setProcessDefinitionKey(userTaskRecord.getProcessDefinitionKey())
        .setUserTaskKey(userTaskRecord.getUserTaskKey())
        .setVariables(objectMapper.writeValueAsString(userTaskRecord.getVariables()))
        .setFollowUpDate(toDateOrNull(userTaskRecord.getFollowUpDate()))
        .setFormKey(userTaskRecord.getFormKey());
  }

  private OffsetDateTime toDateOrNull(String dateString) {
    if (dateString == null)
      return null;
    try {
      return OffsetDateTime.parse(dateString);
    } catch (Exception e) {
      logger.warn("Could not parse {} as OffsetDateTime. Use null.", dateString);
      return null;
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v850.processors.common;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskImplementation;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.util.DateUtil;
import io.camunda.tasklist.zeebeimport.v850.record.Intent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserTaskRecordToTaskEntityMapper {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(UserTaskRecordToTaskEntityMapper.class);

  private static final Set<Intent> SUPPORTED_INTENTS =
      EnumSet.of(Intent.CREATED, Intent.COMPLETED, Intent.CANCELED, Intent.MIGRATED);

  private final FormStore formStore;

  private final ObjectMapper objectMapper;

  public UserTaskRecordToTaskEntityMapper(ObjectMapper objectMapper, FormStore formStore) {
    this.objectMapper = objectMapper;
    this.formStore = formStore;
  }

  public Optional<TaskEntity> map(Record<UserTaskRecordValue> record) {
    final Intent intent = (Intent) record.getIntent();
    LOGGER.debug("Intent {}", intent);
    if (intent == null || !SUPPORTED_INTENTS.contains(intent)) {
      LOGGER.debug("Unsupported intent={}. Skipping it", intent);
      return Optional.empty();
    }
    final UserTaskRecordValue recordValue = record.getValue();
    final String processDefinitionId = String.valueOf(recordValue.getProcessDefinitionKey());
    final TaskEntity entity =
        new TaskEntity()
            .setImplementation(TaskImplementation.ZEEBE_USER_TASK)
            .setId(String.valueOf(record.getKey()))
            .setKey(record.getKey())
            .setPartitionId(record.getPartitionId())
            .setFlowNodeBpmnId(recordValue.getElementId())
            .setFlowNodeInstanceId(String.valueOf(recordValue.getElementInstanceKey()))
            .setProcessInstanceId(String.valueOf(recordValue.getProcessInstanceKey()))
            .setBpmnProcessId(recordValue.getBpmnProcessId())
            .setProcessDefinitionId(processDefinitionId)
            .setTenantId(recordValue.getTenantId());

    switch (intent) {
      case CANCELED -> entity
          .setState(TaskState.CANCELED)
          .setCompletionTime(
              DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
      case COMPLETED -> entity
          .setState(TaskState.COMPLETED)
          .setCompletionTime(
              DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
      case CREATED -> entity
          .setState(TaskState.CREATED)
          .setCreationTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
      case MIGRATED -> entity.setState(TaskState.CREATED);
      default -> {}
    }

    final String dueDate = recordValue.getDueDate();
    if (isNotEmpty(dueDate)) {
      final OffsetDateTime offSetDueDate = DateUtil.toOffsetDateTime(dueDate);
      if (offSetDueDate != null) {
        entity.setDueDate(offSetDueDate);
      }
    }

    final String followUpDate = recordValue.getFollowUpDate();
    if (isNotEmpty(followUpDate)) {
      final OffsetDateTime offSetFollowUpDate = DateUtil.toOffsetDateTime(followUpDate);
      if (offSetFollowUpDate != null) {
        entity.setFollowUpDate(offSetFollowUpDate);
      }
    }

    final long formKey = recordValue.getFormKey();
    if (formKey != -1) {
      final String strFromKey = String.valueOf(formKey);
      entity.setFormKey(strFromKey).setIsFormEmbedded(false);
      formStore
          .getHighestVersionFormByKey(strFromKey)
          .ifPresentOrElse(
              linkedForm -> {
                entity.setFormVersion(linkedForm.version());
                entity.setFormId(linkedForm.bpmnId());
              },
              () -> {
                LOGGER.warn("Form with key={} cannot be found", strFromKey);
              });
    }

    // TODO handle removal of attributes in Zeebe https://github.com/camunda/tasklist/issues/4306

    final String assignee = recordValue.getAssignee();
    if (isNotEmpty(assignee)) {
      entity.setAssignee(assignee);
    }

    final List<String> candidateGroups = recordValue.getCandidateGroupsList();

    if (!candidateGroups.isEmpty()) {
      entity.setCandidateGroups(candidateGroups.toArray(new String[candidateGroups.size()]));
    }

    final List<String> candidateUsers = recordValue.getCandidateUsersList();

    if (!candidateUsers.isEmpty()) {
      entity.setCandidateUsers(candidateUsers.toArray(new String[candidateUsers.size()]));
    }
    return Optional.of(entity);
  }

  public Map<String, Object> getUpdateFieldsMap(TaskEntity entity, Intent intent) {
    final Map<String, Object> updateFields = new HashMap<>();
    if (entity.getState() != null) {
      updateFields.put(TaskTemplate.STATE, entity.getState());
    }
    switch (intent) {
      case MIGRATED -> {
        updateFields.put(TaskTemplate.FLOW_NODE_BPMN_ID, entity.getFlowNodeBpmnId());
        updateFields.put(TaskTemplate.BPMN_PROCESS_ID, entity.getBpmnProcessId());
        updateFields.put(TaskTemplate.PROCESS_DEFINITION_ID, entity.getProcessDefinitionId());
      }
      case COMPLETED, CANCELED -> {
        updateFields.put(TaskTemplate.STATE, entity.getState());
        updateFields.put(TaskTemplate.COMPLETION_TIME, entity.getCompletionTime());
      }
      default -> {}
        // TODO handle update of other fields in https://github.com/camunda/tasklist/issues/4306
    }
    return updateFields;
  }
}

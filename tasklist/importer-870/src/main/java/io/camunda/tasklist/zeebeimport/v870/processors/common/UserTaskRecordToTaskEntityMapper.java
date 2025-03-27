/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.processors.common;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.util.DateUtil;
import io.camunda.tasklist.zeebeimport.v870.record.Intent;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class UserTaskRecordToTaskEntityMapper {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(UserTaskRecordToTaskEntityMapper.class);

  private static final Set<Intent> SUPPORTED_INTENTS =
      EnumSet.of(
          Intent.CREATED,
          Intent.COMPLETED,
          Intent.CANCELED,
          Intent.MIGRATED,
          Intent.ASSIGNED,
          Intent.UPDATED);
  private final FormStore formStore;

  private final ObjectMapper objectMapper;

  public UserTaskRecordToTaskEntityMapper(
      @Qualifier("tasklistObjectMapper") final ObjectMapper objectMapper,
      final FormStore formStore) {
    this.objectMapper = objectMapper;
    this.formStore = formStore;
  }

  public Optional<TaskEntity> map(final Record<UserTaskRecordValue> record) {
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
            .setTenantId(recordValue.getTenantId())
            .setExternalFormReference(
                (recordValue.getExternalFormReference() == null
                        || recordValue.getExternalFormReference().isBlank())
                    ? null
                    : recordValue.getExternalFormReference()) // The recordValue is empty string for
            // externalFormReference - will be fixed on
            // exporters
            .setCustomHeaders(recordValue.getCustomHeaders())
            .setProcessDefinitionVersion(recordValue.getProcessDefinitionVersion())
            .setPriority(recordValue.getPriority());

    switch (intent) {
      case CANCELED ->
          entity
              .setState(TaskState.CANCELED)
              .setCompletionTime(
                  DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
      case COMPLETED ->
          entity
              .setState(TaskState.COMPLETED)
              .setCompletionTime(
                  DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
      case CREATED ->
          entity
              .setState(TaskState.CREATED)
              .setCreationTime(
                  DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
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
          .getFormByKey(strFromKey)
          .ifPresentOrElse(
              linkedForm -> {
                entity.setFormVersion(linkedForm.version());
                entity.setFormId(linkedForm.bpmnId());
              },
              () -> LOGGER.warn("Form with key={} cannot be found", strFromKey));
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

  public Map<String, Object> getUpdateFieldsMap(
      final TaskEntity entity, final Record<UserTaskRecordValue> record) {
    final Map<String, Object> updateFields = new HashMap<>();
    final Intent intent = (Intent) record.getIntent();
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
      case ASSIGNED -> {
        updateFields.put(TaskTemplate.ASSIGNEE, entity.getAssignee());
      }
      case UPDATED -> {
        final UserTaskRecordValue recordValue = record.getValue();
        final List<String> changedAttributes = recordValue.getChangedAttributes();
        for (final String attribute : changedAttributes) {
          switch (attribute) {
            case "candidateGroupsList" ->
                updateFields.put(TaskTemplate.CANDIDATE_GROUPS, entity.getCandidateGroups());
            case "candidateUsersList" ->
                updateFields.put(TaskTemplate.CANDIDATE_USERS, entity.getCandidateUsers());
            case "dueDate" -> updateFields.put(TaskTemplate.DUE_DATE, entity.getDueDate());
            case "followUpDate" ->
                updateFields.put(TaskTemplate.FOLLOW_UP_DATE, entity.getFollowUpDate());
            case "priority" -> updateFields.put(TaskTemplate.PRIORITY, entity.getPriority());
            default -> {
              LOGGER.warn(
                  "Attribute update not mapped while importing ZEEBE_USER_TASKS: {}", attribute);
            }
          }
        }
      }
      default -> {}
        // TODO handle update of other fields in https://github.com/camunda/tasklist/issues/4306
    }
    return updateFields;
  }
}

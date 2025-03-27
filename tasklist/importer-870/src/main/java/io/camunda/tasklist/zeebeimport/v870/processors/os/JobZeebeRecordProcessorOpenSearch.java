/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.processors.os;

import static io.camunda.zeebe.protocol.Protocol.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.util.DateUtil;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.zeebeimport.v870.record.Intent;
import io.camunda.tasklist.zeebeimport.v870.record.value.JobRecordValueImpl;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class JobZeebeRecordProcessorOpenSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(JobZeebeRecordProcessorOpenSearch.class);

  private static final Pattern EMBEDDED_FORMS_PATTERN = Pattern.compile("^camunda-forms:bpmn:.*");

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private FormStore formStore;

  public void processJobRecord(final Record record, final List<BulkOperation> operations) {
    final JobRecordValueImpl recordValue = (JobRecordValueImpl) record.getValue();
    if (recordValue.getType().equals(Protocol.USER_TASK_JOB_TYPE)) {
      if (record.getIntent() != null
          && !record.getIntent().name().equals(Intent.TIMED_OUT.name())) {
        operations.add(persistTask(record, recordValue));
      }
    }
    // else skip task
  }

  private BulkOperation persistTask(final Record record, final JobRecordValueImpl recordValue) {
    final String processDefinitionId = String.valueOf(recordValue.getProcessDefinitionKey());
    final TaskEntity entity =
        new TaskEntity()
            .setImplementation(TaskImplementation.JOB_WORKER)
            .setId(String.valueOf(record.getKey()))
            .setKey(record.getKey())
            .setPartitionId(record.getPartitionId())
            .setFlowNodeBpmnId(recordValue.getElementId())
            .setFlowNodeInstanceId(String.valueOf(recordValue.getElementInstanceKey()))
            .setProcessInstanceId(String.valueOf(recordValue.getProcessInstanceKey()))
            .setBpmnProcessId(recordValue.getBpmnProcessId())
            .setProcessDefinitionId(processDefinitionId)
            .setTenantId(recordValue.getTenantId());

    final String dueDate =
        recordValue.getCustomHeaders().get(Protocol.USER_TASK_DUE_DATE_HEADER_NAME);
    if (dueDate != null) {
      final OffsetDateTime offSetDueDate = DateUtil.toOffsetDateTime(dueDate);
      if (offSetDueDate != null) {
        entity.setDueDate(offSetDueDate);
      }
    }

    final String followUpDate =
        recordValue.getCustomHeaders().get(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME);
    if (followUpDate != null) {
      final OffsetDateTime offSetFollowUpDate = DateUtil.toOffsetDateTime(followUpDate);
      if (offSetFollowUpDate != null) {
        entity.setFollowUpDate(offSetFollowUpDate);
      }
    }

    final String formKey =
        recordValue.getCustomHeaders().get(Protocol.USER_TASK_FORM_KEY_HEADER_NAME);
    entity.setFormKey(formKey);

    Optional.ofNullable(formKey)
        .flatMap(formStore::getFormByKey)
        .ifPresentOrElse(
            linkedForm -> {
              entity.setFormVersion(linkedForm.version());
              entity.setFormId(linkedForm.bpmnId());
              entity.setIsFormEmbedded(false);
            },
            () -> {
              entity.setIsFormEmbedded(
                  formKey != null && EMBEDDED_FORMS_PATTERN.matcher(formKey).matches());
              entity.setFormVersion(null);
              entity.setFormId(null);
            });

    final String assignee = recordValue.getCustomHeaders().get(USER_TASK_ASSIGNEE_HEADER_NAME);
    if (assignee != null) {
      entity.setAssignee(assignee);
    }

    final String candidateGroups =
        recordValue.getCustomHeaders().get(USER_TASK_CANDIDATE_GROUPS_HEADER_NAME);

    if (candidateGroups != null) {
      try {
        entity.setCandidateGroups(objectMapper.readValue(candidateGroups, String[].class));
      } catch (final JsonProcessingException e) {
        LOGGER.warn(
            String.format(
                "Candidate groups can't be parsed from %s: %s", candidateGroups, e.getMessage()),
            e);
      }
    }

    final String candidateUsers =
        recordValue.getCustomHeaders().get(USER_TASK_CANDIDATE_USERS_HEADER_NAME);

    if (candidateUsers != null) {
      try {
        entity.setCandidateUsers(objectMapper.readValue(candidateUsers, String[].class));
      } catch (final JsonProcessingException e) {
        LOGGER.warn(
            String.format(
                "Candidate users can't be parsed from %s: %s", candidateUsers, e.getMessage()),
            e);
      }
    }
    final Intent intent = (Intent) record.getIntent();
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
      case MIGRATED, RECURRED_AFTER_BACKOFF -> entity.setState(TaskState.CREATED);
      case FAILED -> {
        if (recordValue.getRetries() > 0) {
          if (recordValue.getRetryBackoff() > 0) {
            entity.setState(TaskState.FAILED);
          } else {
            entity.setState(TaskState.CREATED);
          }
        } else {
          entity.setState(TaskState.FAILED);
        }
      }
      default -> LOGGER.warn(String.format("Intent %s not supported", intent));
    }
    return getTaskQuery(entity, intent);
  }

  private BulkOperation getTaskQuery(final TaskEntity entity, final Intent intent) {
    LOGGER.debug("Task instance: id {}", entity.getId());
    final Map<String, Object> updateFields = new HashMap<>();
    if (intent == Intent.MIGRATED) {
      updateFields.put(TaskTemplate.FLOW_NODE_BPMN_ID, entity.getFlowNodeBpmnId());
      updateFields.put(TaskTemplate.BPMN_PROCESS_ID, entity.getBpmnProcessId());
      updateFields.put(TaskTemplate.PROCESS_DEFINITION_ID, entity.getProcessDefinitionId());
    } else {
      updateFields.put(TaskTemplate.STATE, entity.getState());
      updateFields.put(TaskTemplate.COMPLETION_TIME, entity.getCompletionTime());
    }
    return new BulkOperation.Builder()
        .update(
            UpdateOperation.of(
                u ->
                    u.index(taskTemplate.getFullQualifiedName())
                        .id(entity.getId())
                        .document(CommonUtils.getJsonObjectFromEntity(updateFields))
                        .upsert(CommonUtils.getJsonObjectFromEntity(entity))
                        .retryOnConflict(OpenSearchUtil.UPDATE_RETRY_COUNT)))
        .build();
  }
}

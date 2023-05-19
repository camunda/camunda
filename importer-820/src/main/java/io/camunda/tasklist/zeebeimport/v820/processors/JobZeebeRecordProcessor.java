/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v820.processors;

import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.camunda.tasklist.zeebeimport.v820.record.Intent.*;
import static io.camunda.zeebe.protocol.Protocol.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.util.DateUtil;
import io.camunda.tasklist.zeebeimport.v820.record.Intent;
import io.camunda.tasklist.zeebeimport.v820.record.value.JobRecordValueImpl;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JobZeebeRecordProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobZeebeRecordProcessor.class);

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private TasklistProperties tasklistProperties;

  public void processJobRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    final JobRecordValueImpl recordValue = (JobRecordValueImpl) record.getValue();
    if (recordValue.getType().equals(Protocol.USER_TASK_JOB_TYPE)) {
      if (record.getIntent() != null
          && !record.getIntent().name().equals(Intent.TIMED_OUT.name())) {
        bulkRequest.add(persistTask(record, recordValue));
      }
    }
    // else skip task
  }

  private UpdateRequest persistTask(Record record, JobRecordValueImpl recordValue)
      throws PersistenceException {
    final String processDefinitionId = String.valueOf(recordValue.getProcessDefinitionKey());
    final TaskEntity entity =
        new TaskEntity()
            .setId(String.valueOf(record.getKey()))
            .setKey(record.getKey())
            .setPartitionId(record.getPartitionId())
            .setFlowNodeBpmnId(recordValue.getElementId())
            .setFlowNodeInstanceId(String.valueOf(recordValue.getElementInstanceKey()))
            .setProcessInstanceId(String.valueOf(recordValue.getProcessInstanceKey()))
            .setBpmnProcessId(recordValue.getBpmnProcessId())
            .setProcessDefinitionId(processDefinitionId);

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

    final String assignee = recordValue.getCustomHeaders().get(USER_TASK_ASSIGNEE_HEADER_NAME);
    if (assignee != null) {
      entity.setAssignee(assignee);
    }

    final String candidateGroups =
        recordValue.getCustomHeaders().get(USER_TASK_CANDIDATE_GROUPS_HEADER_NAME);

    if (candidateGroups != null) {
      try {
        entity.setCandidateGroups(objectMapper.readValue(candidateGroups, String[].class));
      } catch (JsonProcessingException e) {
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
      } catch (JsonProcessingException e) {
        LOGGER.warn(
            String.format(
                "Candidate users can't be parsed from %s: %s", candidateGroups, e.getMessage()),
            e);
      }
    }

    final String taskState = record.getIntent().name();
    LOGGER.debug("JobState {}", taskState);
    if (taskState.equals(CANCELED.name())) {
      entity
          .setState(TaskState.CANCELED)
          .setCompletionTime(
              DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    } else if (taskState.equals(COMPLETED.name())) {
      entity
          .setState(TaskState.COMPLETED)
          .setCompletionTime(
              DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    } else if (taskState.equals(CREATED.name())) {
      entity
          .setState(TaskState.CREATED)
          .setCreationTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    } else {
      LOGGER.warn(String.format("TaskState %s not supported", taskState));
    }
    return getTaskQuery(entity);
  }

  private UpdateRequest getTaskQuery(TaskEntity entity) throws PersistenceException {
    try {
      LOGGER.debug("Task instance: id {}", entity.getId());
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(TaskTemplate.STATE, entity.getState());
      updateFields.put(TaskTemplate.COMPLETION_TIME, entity.getCompletionTime());

      // format date fields properly
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

      return new UpdateRequest()
          .index(taskTemplate.getFullQualifiedName())
          .id(entity.getId())
          .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
          .doc(jsonMap)
          .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to upsert task instance [%s]", entity.getId()),
          e);
    }
  }
}

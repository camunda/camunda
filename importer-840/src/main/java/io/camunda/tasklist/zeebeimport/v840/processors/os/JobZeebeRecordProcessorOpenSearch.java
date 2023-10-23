/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v840.processors.os;

import static io.camunda.tasklist.zeebeimport.v840.record.Intent.*;
import static io.camunda.zeebe.protocol.Protocol.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.util.DateUtil;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.zeebeimport.v840.record.Intent;
import io.camunda.tasklist.zeebeimport.v840.record.value.JobRecordValueImpl;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class JobZeebeRecordProcessorOpenSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(JobZeebeRecordProcessorOpenSearch.class);

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private FormIndex formIndex;

  @Autowired private OpenSearchClient openSearchClient;

  @Autowired private TasklistProperties tasklistProperties;

  public void processJobRecord(Record record, List<BulkOperation> operations)
      throws PersistenceException {
    final JobRecordValueImpl recordValue = (JobRecordValueImpl) record.getValue();
    if (recordValue.getType().equals(Protocol.USER_TASK_JOB_TYPE)) {
      if (record.getIntent() != null
          && !record.getIntent().name().equals(Intent.TIMED_OUT.name())) {
        operations.add(persistTask(record, recordValue));
      }
    }
    // else skip task
  }

  private BulkOperation persistTask(Record record, JobRecordValueImpl recordValue)
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
        .map(this::getHighestVersionFormByKey)
        .ifPresentOrElse(
            linkedForm -> {
              entity.setFormVersion(linkedForm.getVersion());
              entity.setFormId(linkedForm.getBpmnId());
              entity.setIsFormEmbedded(false);
            },
            () -> {
              entity.setIsFormEmbedded(true);
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

  private BulkOperation getTaskQuery(TaskEntity entity) throws PersistenceException {
    LOGGER.debug("Task instance: id {}", entity.getId());
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(TaskTemplate.STATE, entity.getState());
    updateFields.put(TaskTemplate.COMPLETION_TIME, entity.getCompletionTime());

    final JsonObjectBuilder jsonEntityBuilder = CommonUtils.getJsonObjectBuilderForEntity(entity);
    if (entity.getCreationTime() == null) {
      jsonEntityBuilder.remove(TaskTemplate.CREATION_TIME);
    }

    if (entity.getAssignee() == null) {
      jsonEntityBuilder.remove(TaskTemplate.ASSIGNEE);
    }

    return new BulkOperation.Builder()
        .update(
            UpdateOperation.of(
                u ->
                    u.index(taskTemplate.getFullQualifiedName())
                        .id(entity.getId())
                        .document(jsonEntityBuilder.build())
                        .docAsUpsert(true)
                        .retryOnConflict(OpenSearchUtil.UPDATE_RETRY_COUNT)))
        .build();
  }

  private FormEntity getHighestVersionFormByKey(final String formKey) {
    try {

      final var formEntityResponse =
          openSearchClient.search(
              b ->
                  b.index(formIndex.getFullQualifiedName())
                      .query(q -> q.term(t -> t.field(FormIndex.ID).value(FieldValue.of(formKey))))
                      .sort(s -> s.field(f -> f.field(FormIndex.VERSION).order(SortOrder.Desc)))
                      .size(1),
              FormEntity.class);
      if (formEntityResponse.hits().total().value() == 1L) {
        return formEntityResponse.hits().hits().get(0).source();
      } else {
        return null;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

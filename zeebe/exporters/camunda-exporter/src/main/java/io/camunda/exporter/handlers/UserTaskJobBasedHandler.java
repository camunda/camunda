/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.zeebe.protocol.Protocol.USER_TASK_ASSIGNEE_HEADER_NAME;
import static io.camunda.zeebe.protocol.Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME;
import static io.camunda.zeebe.protocol.Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.cache.ExporterEntityCache;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTaskJobBasedHandler implements ExportHandler<TaskEntity, JobRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserTaskJobBasedHandler.class);
  private static final Pattern EMBEDDED_FORMS_PATTERN = Pattern.compile("^camunda-forms:bpmn:.*");
  private static final Set<JobIntent> SUPPORTED_INTENTS =
      EnumSet.of(
          JobIntent.CREATED,
          JobIntent.COMPLETED,
          JobIntent.CANCELED,
          JobIntent.MIGRATED,
          JobIntent.RECURRED_AFTER_BACKOFF,
          JobIntent.FAILED);
  private final ObjectReader objectReader;
  private final String indexName;
  private final ExporterEntityCache<String, CachedFormEntity> formCache;
  private final ExporterMetadata exporterMetadata;

  public UserTaskJobBasedHandler(
      final String indexName,
      final ExporterEntityCache<String, CachedFormEntity> formCache,
      final ExporterMetadata exporterMetadata,
      final ObjectMapper objectMapper) {
    this.indexName = indexName;
    this.formCache = formCache;
    this.exporterMetadata = exporterMetadata;
    objectReader = objectMapper.readerFor(String[].class);
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.JOB;
  }

  @Override
  public Class<TaskEntity> getEntityType() {
    return TaskEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<JobRecordValue> record) {
    return SUPPORTED_INTENTS.contains(record.getIntent())
        && record.getValue().getType().equals(Protocol.USER_TASK_JOB_TYPE);
  }

  @Override
  public List<String> generateIds(final Record<JobRecordValue> record) {
    if (record.getIntent().equals(JobIntent.CREATED)) {
      exporterMetadata.setFirstUserTaskKey(TaskImplementation.JOB_WORKER, record.getKey());
    }
    if (refersToPreviousVersionRecord(record.getKey())) {
      return List.of(String.valueOf(record.getKey()));
    }
    return List.of(String.valueOf(record.getValue().getElementInstanceKey()));
  }

  @Override
  public TaskEntity createNewEntity(final String id) {
    return new TaskEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<JobRecordValue> record, final TaskEntity entity) {
    entity.setProcessInstanceId(String.valueOf(record.getValue().getProcessInstanceKey()));
    entity.setKey(record.getKey());
    switch (record.getIntent()) {
      case JobIntent.CREATED -> createTaskEntity(entity, record);
      case JobIntent.COMPLETED, JobIntent.CANCELED ->
          entity
              .setState(
                  record.getIntent().equals(JobIntent.COMPLETED)
                      ? TaskState.COMPLETED
                      : TaskState.CANCELED)
              .setCompletionTime(
                  ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
      case JobIntent.MIGRATED ->
          entity
              .setFlowNodeBpmnId(record.getValue().getElementId())
              .setBpmnProcessId(record.getValue().getBpmnProcessId())
              .setProcessDefinitionId(String.valueOf(record.getValue().getProcessDefinitionKey()))
              .setState(TaskState.CREATED);
      case JobIntent.RECURRED_AFTER_BACKOFF -> entity.setState(TaskState.CREATED);
      case JobIntent.FAILED -> {
        final var recordValue = record.getValue();
        if (recordValue.getRetries() > 0 && recordValue.getRetryBackoff() <= 0) {
          entity.setState(TaskState.CREATED);
        } else {
          entity.setState(TaskState.FAILED);
        }
      }
      default -> {}
    }

    final TaskJoinRelationship joinRelation = new TaskJoinRelationship();
    joinRelation.setName(TaskJoinRelationshipType.TASK.getType());
    joinRelation.setParent(Long.valueOf(entity.getProcessInstanceId()));
    entity.setJoin(joinRelation);
  }

  @Override
  public void flush(final TaskEntity entity, final BatchRequest batchRequest) {
    final var updateFields = getUpdatedFields(entity);
    final var taskEntityId = entity.getId();
    final var processInstanceKey = entity.getProcessInstanceId();

    final boolean previousVersionRecord = refersToPreviousVersionRecord(entity.getKey());

    batchRequest.upsertWithRouting(
        indexName,
        previousVersionRecord ? String.valueOf(entity.getKey()) : taskEntityId,
        entity,
        updateFields,
        previousVersionRecord ? String.valueOf(entity.getKey()) : processInstanceKey);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private Map<String, Object> getUpdatedFields(final TaskEntity entity) {
    final var updateFields = new HashMap<String, Object>();

    if (entity.getCompletionTime() != null) {
      updateFields.put(TaskTemplate.COMPLETION_TIME, entity.getCompletionTime());
    }
    if (entity.getState() != null) {
      updateFields.put(TaskTemplate.STATE, entity.getState());
    }
    if (entity.getFlowNodeBpmnId() != null) {
      updateFields.put(TaskTemplate.FLOW_NODE_BPMN_ID, entity.getFlowNodeBpmnId());
    }
    if (entity.getProcessDefinitionId() != null) {
      updateFields.put(TaskTemplate.PROCESS_DEFINITION_ID, entity.getProcessDefinitionId());
    }
    if (entity.getBpmnProcessId() != null) {
      updateFields.put(TaskTemplate.BPMN_PROCESS_ID, entity.getBpmnProcessId());
    }

    return updateFields;
  }

  private void createTaskEntity(final TaskEntity entity, final Record<JobRecordValue> record) {
    final var recordValue = record.getValue();
    final var customHeaders = recordValue.getCustomHeaders();

    final var dueDate = customHeaders.get(Protocol.USER_TASK_DUE_DATE_HEADER_NAME);
    final var followUpDate = customHeaders.get(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME);
    final var assignee = customHeaders.get(USER_TASK_ASSIGNEE_HEADER_NAME);
    final var candidateGroups =
        toStringArray(customHeaders.get(USER_TASK_CANDIDATE_GROUPS_HEADER_NAME));
    final var candidateUsers =
        toStringArray(customHeaders.get(USER_TASK_CANDIDATE_USERS_HEADER_NAME));
    final var formKey = customHeaders.get(Protocol.USER_TASK_FORM_KEY_HEADER_NAME);

    entity
        .setImplementation(TaskImplementation.JOB_WORKER)
        .setKey(record.getKey())
        .setState(TaskState.CREATED)
        .setAssignee(ExporterUtil.isEmpty(assignee) ? null : assignee)
        .setDueDate(ExporterUtil.toOffsetDateTime(dueDate))
        .setFollowUpDate(ExporterUtil.toOffsetDateTime(followUpDate))
        .setFlowNodeInstanceId(String.valueOf(recordValue.getElementInstanceKey()))
        .setProcessInstanceId(String.valueOf(recordValue.getProcessInstanceKey()))
        .setFlowNodeBpmnId(recordValue.getElementId())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setProcessDefinitionId(String.valueOf(recordValue.getProcessDefinitionKey()))
        .setProcessDefinitionVersion(recordValue.getProcessDefinitionVersion())
        .setPartitionId(record.getPartitionId())
        .setTenantId(recordValue.getTenantId())
        .setPosition(record.getPosition())
        .setCreationTime(
            ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));

    if (candidateUsers != null && candidateUsers.length > 0) {
      entity.setCandidateUsers(candidateUsers);
    }

    if (candidateGroups != null && candidateGroups.length > 0) {
      entity.setCandidateGroups(candidateGroups);
    }

    if (!ExporterUtil.isEmpty(formKey)) {
      final var isEmbeddedForm = EMBEDDED_FORMS_PATTERN.matcher(formKey).matches();
      entity.setFormKey(formKey);
      entity.setIsFormEmbedded(isEmbeddedForm);

      if (!isEmbeddedForm) {
        formCache
            .get(formKey)
            .ifPresent(c -> entity.setFormId(c.formId()).setFormVersion(c.formVersion()));
      }
    }
  }

  private String[] toStringArray(final String value) {
    if (!ExporterUtil.isEmpty(value)) {
      try {
        return objectReader.readValue(value);
      } catch (final JsonProcessingException e) {
        LOGGER.warn(String.format("Failed to parse value %s: %s", value, e.getMessage()), e);
      }
    }
    return null;
  }

  private boolean refersToPreviousVersionRecord(final long key) {
    return exporterMetadata.getFirstUserTaskKey(TaskImplementation.JOB_WORKER) == -1
        || key < exporterMetadata.getFirstUserTaskKey(TaskImplementation.JOB_WORKER);
  }
}

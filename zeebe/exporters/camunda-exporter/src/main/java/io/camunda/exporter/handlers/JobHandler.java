/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.operate.template.JobTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.JobTemplate.CUSTOM_HEADERS;
import static io.camunda.webapps.schema.descriptors.operate.template.JobTemplate.ERROR_CODE;
import static io.camunda.webapps.schema.descriptors.operate.template.JobTemplate.ERROR_MESSAGE;
import static io.camunda.webapps.schema.descriptors.operate.template.JobTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.JobTemplate.JOB_DEADLINE;
import static io.camunda.webapps.schema.descriptors.operate.template.JobTemplate.JOB_DENIED;
import static io.camunda.webapps.schema.descriptors.operate.template.JobTemplate.JOB_DENIED_REASON;
import static io.camunda.webapps.schema.descriptors.operate.template.JobTemplate.JOB_FAILED_WITH_RETRIES_LEFT;
import static io.camunda.webapps.schema.descriptors.operate.template.JobTemplate.JOB_STATE;
import static io.camunda.webapps.schema.descriptors.operate.template.JobTemplate.JOB_WORKER;
import static io.camunda.webapps.schema.descriptors.operate.template.JobTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.JobTemplate.RETRIES;
import static io.camunda.webapps.schema.descriptors.operate.template.JobTemplate.TIME;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operate.JobEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JobHandler implements ExportHandler<JobEntity, JobRecordValue> {
  protected static final Set<JobIntent> JOB_EVENTS =
      Set.of(
          JobIntent.CREATED,
          JobIntent.COMPLETED,
          JobIntent.TIMED_OUT,
          JobIntent.FAILED,
          JobIntent.RETRIES_UPDATED,
          JobIntent.CANCELED,
          JobIntent.ERROR_THROWN,
          JobIntent.MIGRATED);
  private static final Set<JobIntent> FAILED_JOB_EVENTS =
      Set.of(JobIntent.FAILED, JobIntent.ERROR_THROWN);

  protected final String indexName;

  public JobHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.JOB;
  }

  @Override
  public Class<JobEntity> getEntityType() {
    return JobEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<JobRecordValue> record) {
    final JobIntent intent = (JobIntent) record.getIntent();
    return JOB_EVENTS.contains(intent);
  }

  @Override
  public List<String> generateIds(final Record<JobRecordValue> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public JobEntity createNewEntity(final String id) {
    return new JobEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<JobRecordValue> record, final JobEntity entity) {

    final JobRecordValue recordValue = record.getValue();
    entity.setKey(record.getKey());
    entity
        .setPartitionId(record.getPartitionId())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setFlowNodeInstanceId(recordValue.getElementInstanceKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setTenantId(recordValue.getTenantId())
        .setType(recordValue.getType())
        .setWorker(recordValue.getWorker())
        .setState(record.getIntent().name())
        .setRetries(recordValue.getRetries())
        .setErrorMessage(recordValue.getErrorMessage())
        .setErrorCode(recordValue.getErrorCode())
        .setEndTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
        .setCustomHeaders(recordValue.getCustomHeaders())
        .setJobKind(recordValue.getJobKind().name())
        .setFlowNodeId(recordValue.getElementId());

    if (record.getIntent() == JobIntent.COMPLETED) {
      entity
          .setDenied(recordValue.getResult().isDenied())
          .setDeniedReason(recordValue.getResult().getDeniedReason());
    }

    if (recordValue.getJobListenerEventType() != null) {
      entity.setListenerEventType(recordValue.getJobListenerEventType().name());
    }
    final long jobDeadline = recordValue.getDeadline();
    if (jobDeadline >= 0) {
      entity.setDeadline(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(jobDeadline)));
    }

    if (FAILED_JOB_EVENTS.contains(record.getIntent())) {
      // set flowNodeId to null to not overwrite it (because zeebe puts an error message there)
      entity.setFlowNodeId(null);
      if (recordValue.getRetries() > 0) {
        entity.setJobFailedWithRetriesLeft(true);
      } else {
        entity.setJobFailedWithRetriesLeft(false);
      }
    }
  }

  @Override
  public void flush(final JobEntity jobEntity, final BatchRequest batchRequest) {
    final Map<String, Object> updateFields = new HashMap<>();
    if (jobEntity.getFlowNodeId() != null) {
      updateFields.put(FLOW_NODE_ID, jobEntity.getFlowNodeId());
    }
    if (jobEntity.getDeniedReason() != null) {
      updateFields.put(JOB_DENIED_REASON, jobEntity.getDeniedReason());
    }
    if (jobEntity.isDenied() != null) {
      updateFields.put(JOB_DENIED, jobEntity.isDenied());
    }
    updateFields.put(JOB_WORKER, jobEntity.getWorker());
    updateFields.put(JOB_STATE, jobEntity.getState());
    updateFields.put(RETRIES, jobEntity.getRetries());
    updateFields.put(ERROR_MESSAGE, jobEntity.getErrorMessage());
    updateFields.put(ERROR_CODE, jobEntity.getErrorCode());
    updateFields.put(TIME, jobEntity.getEndTime());
    updateFields.put(CUSTOM_HEADERS, jobEntity.getCustomHeaders());
    updateFields.put(JOB_DEADLINE, jobEntity.getDeadline());
    updateFields.put(PROCESS_DEFINITION_KEY, jobEntity.getProcessDefinitionKey());
    updateFields.put(BPMN_PROCESS_ID, jobEntity.getBpmnProcessId());
    if (FAILED_JOB_EVENTS.stream().anyMatch(i -> jobEntity.getState().equals(i.name()))) {
      updateFields.put(JOB_FAILED_WITH_RETRIES_LEFT, jobEntity.isJobFailedWithRetriesLeft());
    }
    batchRequest.upsert(indexName, jobEntity.getId(), jobEntity, updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}

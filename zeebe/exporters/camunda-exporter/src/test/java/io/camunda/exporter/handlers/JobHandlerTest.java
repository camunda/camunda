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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.operate.template.JobTemplate;
import io.camunda.webapps.schema.entities.operate.JobEntity;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.DateUtil;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.Mockito;

final class JobHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = JobTemplate.INDEX_NAME;

  private final JobHandler underTest = new JobHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.JOB);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(JobEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = JobIntent.class,
      names = {
        "CREATED",
        "COMPLETED",
        "TIMED_OUT",
        "FAILED",
        "RETRIES_UPDATED",
        "CANCELED",
        "ERROR_THROWN",
        "MIGRATED"
      },
      mode = Mode.INCLUDE)
  void shouldHandleRecord(final JobIntent intent) {
    // given
    final Record<JobRecordValue> record = generateRecord(intent);

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = JobIntent.class,
      names = {
        "CREATED",
        "COMPLETED",
        "TIMED_OUT",
        "FAILED",
        "RETRIES_UPDATED",
        "CANCELED",
        "ERROR_THROWN",
        "MIGRATED"
      },
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecord(final JobIntent intent) {
    // given
    final Record<JobRecordValue> record = generateRecord(intent);

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void testGenerateIds() {
    // given
    final Record<JobRecordValue> record = factory.generateRecord(ValueType.JOB);

    // when
    final var ids = underTest.generateIds(record);

    // then
    assertThat(ids).containsExactly(String.valueOf(record.getKey()));
  }

  @Test
  void testCreateNewEntity() {
    // given
    final String id = "id";

    // when
    final var entity = underTest.createNewEntity(id);

    // then
    assertThat(entity).isNotNull();
    assertThat(entity.getId()).isEqualTo(id);
  }

  @Test
  void testUpdateEntity() {
    // given
    final long recordKey = 789;
    final int partitionId = 10;
    final int processInstanceKey = 123;
    final long processDefinitionKey = 555L;
    final int elementInstanceKey = 456;
    final String elementId = "elementId";
    final String bpmnProcessId = "bpmnProcessId";
    final String tenantId = "tenantId";
    final String jobType = "jobType";
    final int retries = 3;
    final String jobWorker = "jobWorker";
    final String errorMessage = "someErrorMessage";
    final String errorCode = "errorCode";
    final long deadline = Instant.now().toEpochMilli();
    final JobKind jobKind = JobKind.BPMN_ELEMENT;
    final JobListenerEventType jobListenerEventType = JobListenerEventType.END;
    final Boolean jobDenied = true;
    final String jobDeniedReason = "reason to deny";
    final var recordValue =
        ImmutableJobRecordValue.builder()
            .withProcessInstanceKey(processInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withElementId(elementId)
            .withBpmnProcessId(bpmnProcessId)
            .withProcessDefinitionKey(processDefinitionKey)
            .withType(jobType)
            .withRetries(retries)
            .withWorker(jobWorker)
            .withCustomHeaders(Map.of("key", "val"))
            .withTenantId(tenantId)
            .withErrorMessage(errorMessage)
            .withJobKind(jobKind)
            .withJobListenerEventType(jobListenerEventType)
            .withDeadline(deadline)
            .withErrorCode(errorCode)
            .withResult(new JobResult().setDenied(jobDenied).setDeniedReason(jobDeniedReason))
            .build();
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.CREATED)
                    .withKey(recordKey)
                    .withPartitionId(partitionId)
                    .withValueType(ValueType.JOB)
                    .withValue(recordValue));
    final var entity = new JobEntity().setId(String.valueOf(recordKey));

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo(String.valueOf(recordKey));
    assertThat(entity.getKey()).isEqualTo(recordKey);
    assertThat(entity.getPartitionId()).isEqualTo(partitionId);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(entity.getBpmnProcessId()).isEqualTo(bpmnProcessId);
    assertThat(entity.getFlowNodeInstanceId()).isEqualTo(elementInstanceKey);
    assertThat(entity.getFlowNodeId()).isEqualTo(elementId);
    assertThat(entity.getTenantId()).isEqualTo(tenantId);
    assertThat(entity.getType()).isEqualTo(jobType);
    assertThat(entity.getJobKind()).isEqualTo(jobKind.name());
    assertThat(entity.getListenerEventType()).isEqualTo(jobListenerEventType.name());
    assertThat(entity.getRetries()).isEqualTo(retries);
    assertThat(entity.getWorker()).isEqualTo(jobWorker);
    assertThat(entity.getCustomHeaders()).isEqualTo(Map.of("key", "val"));
    assertThat(entity.getState()).isEqualTo("CREATED");
    assertThat(entity.getErrorMessage()).isEqualTo(errorMessage);
    assertThat(entity.getErrorCode()).isEqualTo(errorCode);
    assertThat(entity.isJobFailedWithRetriesLeft()).isFalse();
    assertThat(entity.getEndTime())
        .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    assertThat(entity.getDeadline())
        .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(deadline)));
    assertThat(entity.isJobDenied()).isEqualTo(jobDenied);
    assertThat(entity.getJobDeniedReason()).isEqualTo(jobDeniedReason);
  }

  @Test
  void testUpdateEntityWithFailedIntentAndRetriesLeft() {
    // given
    final long recordKey = 789;
    final String elementId = "elementId";
    final int retries = 1;
    final var recordValue =
        ImmutableJobRecordValue.builder()
            .withElementId(elementId)
            .withRetries(retries)
            .withJobKind(JobKind.BPMN_ELEMENT)
            .build();
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.FAILED)
                    .withKey(recordKey)
                    .withValueType(ValueType.JOB)
                    .withValue(recordValue));
    final var entity = new JobEntity().setId(String.valueOf(recordKey));

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo(String.valueOf(recordKey));
    assertThat(entity.getKey()).isEqualTo(recordKey);
    assertThat(entity.getFlowNodeId()).isNull();
    assertThat(entity.isJobFailedWithRetriesLeft()).isTrue();
  }

  @Test
  void testUpdateEntityWithFailedIntentAndRetriesExhausted() {
    // given
    final long recordKey = 789;
    final String elementId = "elementId";
    final int retries = 0;
    final var recordValue =
        ImmutableJobRecordValue.builder()
            .withElementId(elementId)
            .withRetries(retries)
            .withJobKind(JobKind.BPMN_ELEMENT)
            .build();
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.FAILED)
                    .withKey(recordKey)
                    .withValueType(ValueType.JOB)
                    .withValue(recordValue));
    final var entity = new JobEntity().setId(String.valueOf(recordKey));

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo(String.valueOf(recordKey));
    assertThat(entity.getKey()).isEqualTo(recordKey);
    assertThat(entity.getFlowNodeId()).isNull();
    assertThat(entity.isJobFailedWithRetriesLeft()).isFalse();
  }

  @Test
  void shouldUpsertEntityOnFlush() {
    // given
    final String jobId = "111";
    final String expectedIndexName = JobTemplate.INDEX_NAME;
    final int retries = 3;
    final String jobWorker = "jobWorker";
    final String errorMessage = "someErrorMessage";
    final String errorCode = "errorCode";
    final OffsetDateTime deadline = OffsetDateTime.now().plus(1, ChronoUnit.DAYS);
    final String state = "CREATED";
    final String bpmnProcessId = "bpmnProcessId";
    final long processDefinitionKey = 555L;
    final OffsetDateTime endTime = OffsetDateTime.now();
    final Boolean jobDenied = true;
    final String jobDeniedReason = "reason to deny";

    final Map<String, String> customHeaders = Map.of("key", "val");
    final JobEntity jobEntity =
        new JobEntity()
            .setId(jobId)
            .setWorker(jobWorker)
            .setState(state)
            .setRetries(retries)
            .setErrorMessage(errorMessage)
            .setErrorCode(errorCode)
            .setEndTime(endTime)
            .setCustomHeaders(customHeaders)
            .setDeadline(deadline)
            .setProcessDefinitionKey(processDefinitionKey)
            .setBpmnProcessId(bpmnProcessId)
            .setJobDenied(jobDenied)
            .setJobDeniedReason(jobDeniedReason);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(JOB_WORKER, jobEntity.getWorker());
    expectedUpdateFields.put(JOB_STATE, jobEntity.getState());
    expectedUpdateFields.put(RETRIES, jobEntity.getRetries());
    expectedUpdateFields.put(ERROR_MESSAGE, jobEntity.getErrorMessage());
    expectedUpdateFields.put(ERROR_CODE, jobEntity.getErrorCode());
    expectedUpdateFields.put(TIME, jobEntity.getEndTime());
    expectedUpdateFields.put(CUSTOM_HEADERS, jobEntity.getCustomHeaders());
    expectedUpdateFields.put(JOB_DEADLINE, jobEntity.getDeadline());
    expectedUpdateFields.put(PROCESS_DEFINITION_KEY, jobEntity.getProcessDefinitionKey());
    expectedUpdateFields.put(BPMN_PROCESS_ID, jobEntity.getBpmnProcessId());
    expectedUpdateFields.put(JOB_DENIED, jobDenied);
    expectedUpdateFields.put(JOB_DENIED_REASON, jobDeniedReason);

    final BatchRequest mockRequest = Mockito.mock(BatchRequest.class);

    // when
    underTest.flush(jobEntity, mockRequest);

    // then
    verify(mockRequest, times(1))
        .upsert(expectedIndexName, jobEntity.getId(), jobEntity, expectedUpdateFields);
  }

  @Test
  void shouldUpsertWithJobFailedWithRetriesAndFlowNodeId() {
    // given
    final String jobId = "111";
    final String expectedIndexName = JobTemplate.INDEX_NAME;
    final String elementId = "elementId";
    final int retries = 2;
    final String jobWorker = "jobWorker";
    final String errorMessage = "someErrorMessage";
    final String errorCode = "errorCode";
    final OffsetDateTime deadline = OffsetDateTime.now().plus(1, ChronoUnit.DAYS);
    final String state = "FAILED";
    final String bpmnProcessId = "bpmnProcessId";
    final long processDefinitionKey = 555L;
    final OffsetDateTime endTime = OffsetDateTime.now();
    final Boolean jobDenied = true;
    final String jobDeniedReason = "reason to deny";

    final Map<String, String> customHeaders = Map.of("key", "val");
    final JobEntity jobEntity =
        new JobEntity()
            .setId(jobId)
            .setFlowNodeId(elementId)
            .setWorker(jobWorker)
            .setState(state)
            .setRetries(retries)
            .setErrorMessage(errorMessage)
            .setErrorCode(errorCode)
            .setEndTime(endTime)
            .setCustomHeaders(customHeaders)
            .setDeadline(deadline)
            .setProcessDefinitionKey(processDefinitionKey)
            .setBpmnProcessId(bpmnProcessId)
            .setJobFailedWithRetriesLeft(true)
            .setJobDenied(jobDenied)
            .setJobDeniedReason(jobDeniedReason);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(FLOW_NODE_ID, jobEntity.getFlowNodeId());
    expectedUpdateFields.put(JOB_FAILED_WITH_RETRIES_LEFT, jobEntity.isJobFailedWithRetriesLeft());
    expectedUpdateFields.put(JOB_WORKER, jobEntity.getWorker());
    expectedUpdateFields.put(JOB_STATE, jobEntity.getState());
    expectedUpdateFields.put(RETRIES, jobEntity.getRetries());
    expectedUpdateFields.put(ERROR_MESSAGE, jobEntity.getErrorMessage());
    expectedUpdateFields.put(ERROR_CODE, jobEntity.getErrorCode());
    expectedUpdateFields.put(TIME, jobEntity.getEndTime());
    expectedUpdateFields.put(CUSTOM_HEADERS, jobEntity.getCustomHeaders());
    expectedUpdateFields.put(JOB_DEADLINE, jobEntity.getDeadline());
    expectedUpdateFields.put(PROCESS_DEFINITION_KEY, jobEntity.getProcessDefinitionKey());
    expectedUpdateFields.put(BPMN_PROCESS_ID, jobEntity.getBpmnProcessId());
    expectedUpdateFields.put(JOB_DENIED, jobDenied);
    expectedUpdateFields.put(JOB_DENIED_REASON, jobDeniedReason);

    final BatchRequest mockRequest = Mockito.mock(BatchRequest.class);

    // when
    underTest.flush(jobEntity, mockRequest);

    // then
    verify(mockRequest, times(1))
        .upsert(expectedIndexName, jobEntity.getId(), jobEntity, expectedUpdateFields);
  }

  private Record<JobRecordValue> generateRecord(final JobIntent intent) {
    return factory.generateRecord(ValueType.JOB, r -> r.withIntent(intent));
  }

  private void assertShouldHandleRecord(final Record<JobRecordValue> record) {
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  private void assertShouldNotHandleRecord(final Record<JobRecordValue> record) {
    assertThat(underTest.handlesRecord(record)).isFalse();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.cache.TestFormCache;
import io.camunda.exporter.cache.TestProcessCache;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UserTaskJobBasedHandlerTest {
  private static final Set<JobIntent> SUPPORTED_INTENTS =
      EnumSet.of(
          JobIntent.CREATED,
          JobIntent.COMPLETED,
          JobIntent.CANCELED,
          JobIntent.MIGRATED,
          JobIntent.RECURRED_AFTER_BACKOFF,
          JobIntent.FAILED);

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-tasklist-task";
  private final TestFormCache formCache = new TestFormCache();
  private final TestProcessCache processCache = new TestProcessCache();
  private final ExporterMetadata exporterMetadata =
      new ExporterMetadata(TestObjectMapper.objectMapper());
  private final UserTaskJobBasedHandler underTest =
      new UserTaskJobBasedHandler(
          indexName, formCache, processCache, exporterMetadata, TestObjectMapper.objectMapper());

  @BeforeEach
  void resetMetadata() {
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.JOB_WORKER, -1);
  }

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.JOB);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(TaskEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType(Protocol.USER_TASK_JOB_TYPE)
            .withJobToUserTaskMigration(false)
            .build();

    SUPPORTED_INTENTS.forEach(
        intent -> {
          final Record<JobRecordValue> jobRecord =
              factory.generateRecord(
                  ValueType.JOB, r -> r.withIntent(intent).withValue(jobRecordValue));
          // when - then
          assertThat(underTest.handlesRecord(jobRecord)).isTrue();
        });
  }

  @Test
  void shouldNotHandleNonCanceledMigrationRecord() {
    // given: migration=true records that are NOT CANCELED must never be handled
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType(Protocol.USER_TASK_JOB_TYPE)
            .withJobToUserTaskMigration(true)
            .build();

    SUPPORTED_INTENTS.stream()
        .filter(intent -> !intent.equals(JobIntent.CANCELED))
        .forEach(
            intent -> {
              final Record<JobRecordValue> jobRecord =
                  factory.generateRecord(
                      ValueType.JOB, r -> r.withIntent(intent).withValue(jobRecordValue));
              assertThat(underTest.handlesRecord(jobRecord)).isFalse();
            });
  }

  @Test
  void shouldHandleRecordForMigrationCancelWithLegacyKey() {
    // given: CANCELED + migration=true + key below watermark → true (stale-doc delete)
    final long firstUserTaskKey = 100;
    final long legacyJobKey = 90;
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.JOB_WORKER, firstUserTaskKey);

    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType(Protocol.USER_TASK_JOB_TYPE)
            .withJobToUserTaskMigration(true)
            .build();

    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r -> r.withIntent(JobIntent.CANCELED).withKey(legacyJobKey).withValue(value));

    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @Test
  void shouldNotHandleRecordForMigrationCancelWithCurrentVersionKey() {
    // given: CANCELED + migration=true + key above watermark → false (current-version, no delete)
    final long firstUserTaskKey = 100;
    final long currentJobKey = 110;
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.JOB_WORKER, firstUserTaskKey);

    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType(Protocol.USER_TASK_JOB_TYPE)
            .withJobToUserTaskMigration(true)
            .build();

    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r -> r.withIntent(JobIntent.CANCELED).withKey(currentJobKey).withValue(value));

    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldHandleRecordForMigrationCancelWhenWatermarkUnset() {
    // given: CANCELED + migration=true + watermark=-1 → any key treated as legacy → true
    // (watermark reset to -1 in @BeforeEach)
    final long anyJobKey = 12345;

    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType(Protocol.USER_TASK_JOB_TYPE)
            .withJobToUserTaskMigration(true)
            .build();

    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r -> r.withIntent(JobIntent.CANCELED).withKey(anyJobKey).withValue(value));

    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @Test
  void shouldNotHandleRecord() {
    // given
    Arrays.stream(JobIntent.values())
        .filter(intent -> !SUPPORTED_INTENTS.contains(intent))
        .forEach(
            intent -> {
              final Record<JobRecordValue> jobRecord =
                  factory.generateRecord(ValueType.JOB, r -> r.withIntent(intent));
              // when - then
              assertThat(underTest.handlesRecord(jobRecord)).isFalse();
            });
  }

  @Test
  void shouldNotHandleNoneUserTaskJobBased() {
    // given
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType("not-user-task-job-based")
            .build();

    SUPPORTED_INTENTS.forEach(
        intent -> {
          final Record<JobRecordValue> jobRecord =
              factory.generateRecord(
                  ValueType.JOB, r -> r.withIntent(intent).withValue(jobRecordValue));
          // when - then
          assertThat(underTest.handlesRecord(jobRecord)).isFalse();
        });
  }

  @Test
  void shouldGenerateIdForNewVersionReferenceRecord() {
    // given
    /* For 8.7 Ingested records, the recordKey has to be greater than the firstIngestedUserTaskKey */
    final long firstIngestedUserTaskKey = 100;
    final long recordKey = 110;
    final long processInstanceKey = 111;
    final long flowNodeInstanceKey = 211;
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.JOB_WORKER, firstIngestedUserTaskKey);
    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.CREATED)
                    .withKey(recordKey)
                    .withValue(
                        ImmutableJobRecordValue.builder()
                            .withProcessInstanceKey(processInstanceKey)
                            .withElementInstanceKey(flowNodeInstanceKey)
                            .build()));

    // when
    final var idList = underTest.generateIds(jobRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(flowNodeInstanceKey));
  }

  @Test
  void shouldGenerateIdForPreviousVersionReferenceRecord() {
    // given
    /* For 8.7 Ingested records, the recordKey has to be greater than the firstIngestedUserTaskKey */
    final long firstIngestedUserTaskKey = 100;
    final long recordKey = 90;
    final long processInstanceKey = 111;
    final long flowNodeInstanceKey = 211;
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.JOB_WORKER, firstIngestedUserTaskKey);
    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.CREATED)
                    .withKey(recordKey)
                    .withValue(
                        ImmutableJobRecordValue.builder()
                            .withProcessInstanceKey(processInstanceKey)
                            .withElementInstanceKey(flowNodeInstanceKey)
                            .build()));

    // when
    final var idList = underTest.generateIds(jobRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(recordKey));
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldAddEntityOnFlushForNewVersionReferenceRecord() {

    // given
    final long firstIngestedUserTaskKey = 100;
    final long recordKey = 110;
    final long processInstanceKey = 111;
    final long flowNodeInstanceKey = 211;
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.JOB_WORKER, firstIngestedUserTaskKey);
    final TaskEntity inputEntity =
        new TaskEntity()
            .setId(String.valueOf(flowNodeInstanceKey))
            .setKey(recordKey)
            .setProcessInstanceId(String.valueOf(processInstanceKey))
            .setFlowNodeInstanceId(String.valueOf(flowNodeInstanceKey));
    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, inputEntity, mockRequest);

    // then
    final Map<String, Object> updateFieldsMap = new HashMap<>();

    verify(mockRequest, times(1))
        .upsertWithRouting(
            index,
            String.valueOf(flowNodeInstanceKey),
            inputEntity,
            updateFieldsMap,
            String.valueOf(processInstanceKey));
  }

  @Test
  void shouldAddEntityOnFlushForPreviousVersionReferenceRecord() {

    // given
    final long firstIngestedUserTaskKey = 100;
    final long recordKey = 90;
    final long processInstanceKey = 111;
    final long flowNodeInstanceKey = 211;
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.JOB_WORKER, firstIngestedUserTaskKey);
    final TaskEntity inputEntity =
        new TaskEntity()
            .setId(String.valueOf(recordKey))
            .setKey(recordKey)
            .setState(TaskState.CREATED)
            .setProcessInstanceId(String.valueOf(processInstanceKey))
            .setFlowNodeInstanceId(String.valueOf(flowNodeInstanceKey));
    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, inputEntity, mockRequest);

    // then
    final Map<String, Object> updateFieldsMap = new HashMap<>();
    updateFieldsMap.put(TaskTemplate.STATE, TaskState.CREATED);

    verify(mockRequest, times(1))
        .upsertWithRouting(
            index,
            String.valueOf(recordKey),
            inputEntity,
            updateFieldsMap,
            String.valueOf(recordKey));
  }

  void shouldUpdateEntityFromRecord() {
    // given
    final long processInstanceKey = 123;
    final long processDefinitionKey = 555;
    final long flowNodeInstanceKey = 456;
    final long recordKey = 110;
    final long rootProcessInstanceKey = 999;
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK, 100);
    final var dateTime = OffsetDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    final var assignee = "foo";
    final var formKey = "my-local-form-key";
    final var elementId = "elementId";

    final var customerHeaders = new HashMap<String, String>();
    customerHeaders.put(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, assignee);
    customerHeaders.put(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, dateTime);
    customerHeaders.put(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME, dateTime);
    customerHeaders.put(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "[\"user1\", \"user2\"]");
    customerHeaders.put(
        Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, "[\"group1\", \"group2\"]");
    customerHeaders.put(Protocol.USER_TASK_FORM_KEY_HEADER_NAME, formKey);

    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withCustomHeaders(customerHeaders)
            .withProcessInstanceKey(processInstanceKey)
            .withProcessDefinitionKey(processDefinitionKey)
            .withElementInstanceKey(flowNodeInstanceKey)
            .withElementId(elementId)
            .withRootProcessInstanceKey(rootProcessInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.CREATED)
                    .withKey(recordKey)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    formCache.put(formKey, new CachedFormEntity("my-form", 987L));
    processCache.put(
        processDefinitionKey,
        new CachedProcessEntity(
            "my-process", 1, "v1", List.of(), Map.of(elementId, "my-flow-node"), false, Map.of()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId(String.valueOf(recordKey));
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getId()).isEqualTo(String.valueOf(recordKey));
    assertThat(taskEntity.getKey()).isEqualTo(jobRecord.getKey());
    assertThat(taskEntity.getTenantId()).isEqualTo(jobRecordValue.getTenantId());
    assertThat(taskEntity.getPartitionId()).isEqualTo(jobRecord.getPartitionId());
    assertThat(taskEntity.getPosition()).isEqualTo(jobRecord.getPosition());
    assertThat(taskEntity.getProcessInstanceId()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(taskEntity.getFlowNodeBpmnId()).isEqualTo(jobRecordValue.getElementId());
    assertThat(taskEntity.getName()).isEqualTo("my-flow-node");
    assertThat(taskEntity.getBpmnProcessId()).isEqualTo(jobRecordValue.getBpmnProcessId());
    assertThat(taskEntity.getProcessDefinitionId())
        .isEqualTo(String.valueOf(jobRecordValue.getProcessDefinitionKey()));
    assertThat(taskEntity.getProcessDefinitionVersion())
        .isEqualTo(jobRecordValue.getProcessDefinitionVersion());
    assertThat(taskEntity.getFormKey()).isEqualTo(formKey);
    assertThat(taskEntity.getFormId()).isEqualTo("my-form");
    assertThat(taskEntity.getFormVersion()).isEqualTo(987L);
    assertThat(taskEntity.getIsFormEmbedded()).isFalse();
    assertThat(taskEntity.getExternalFormReference()).isNull();
    assertThat(taskEntity.getCustomHeaders()).isNull();
    assertThat(taskEntity.getPriority()).isNull();
    assertThat(taskEntity.getAction()).isNull();
    assertThat(taskEntity.getDueDate()).isEqualTo(dateTime);
    assertThat(taskEntity.getFollowUpDate()).isEqualTo(dateTime);
    assertThat(Arrays.stream(taskEntity.getCandidateGroups()).toList())
        .contains("group1", "group1");
    assertThat(Arrays.stream(taskEntity.getCandidateUsers()).toList()).contains("user1", "user2");
    assertThat(taskEntity.getAssignee()).isEqualTo(assignee);
    assertThat(taskEntity.getJoin()).isNotNull();
    assertThat(taskEntity.getJoin().getParent()).isEqualTo(jobRecordValue.getProcessInstanceKey());
    assertThat(taskEntity.getJoin().getName()).isEqualTo(TaskJoinRelationshipType.TASK.getType());
    assertThat(taskEntity.getState()).isEqualTo(TaskState.CREATED);
    assertThat(taskEntity.getCreationTime())
        .isEqualTo(
            ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(jobRecord.getTimestamp())));
    assertThat(taskEntity.getImplementation()).isEqualTo(TaskImplementation.JOB_WORKER);
    assertThat(taskEntity.getRootProcessInstanceKey()).isEqualTo(rootProcessInstanceKey);
  }

  @Test
  void shouldNotSetRootProcessInstanceKeyWhenDefault() {
    // given
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withRootProcessInstanceKey(-1)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB, r -> r.withIntent(JobIntent.CREATED).withValue(jobRecordValue));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("123");
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldNotSetFormIdAndVersionIfCamundaEmbeddedForm() {
    // given
    final long processInstanceKey = 123;
    final long jobKey = 456;
    final var formKey = "camunda-forms:bpmn:my-form";

    final var customerHeaders = new HashMap<String, String>();
    customerHeaders.put(Protocol.USER_TASK_FORM_KEY_HEADER_NAME, formKey);

    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withCustomHeaders(customerHeaders)
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.CREATED)
                    .withKey(jobKey)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    formCache.put("formId", new CachedFormEntity("my-form", 987L));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId(String.valueOf(jobKey));
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getFormKey()).isEqualTo(formKey);
    assertThat(taskEntity.getFormId()).isNull();
    assertThat(taskEntity.getFormVersion()).isNull();
    assertThat(taskEntity.getIsFormEmbedded()).isTrue();
  }

  @Test
  void shouldUpdateEntityFromRecordForCanceledIntent() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withProcessInstanceKey(processInstanceKey)
            .withJobToUserTaskMigration(false)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.CANCELED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.CANCELED);
    assertThat(taskEntity.getCompletionTime())
        .isEqualTo(
            ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(jobRecord.getTimestamp())));
  }

  @Test
  void shouldUpdateEntityFromRecordForCompletedIntent() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.COMPLETED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.COMPLETED);
    assertThat(taskEntity.getCompletionTime())
        .isEqualTo(
            ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(jobRecord.getTimestamp())));
  }

  @Test
  void shouldUpdateEntityFromRecordForMigratedIntent() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder().withProcessInstanceKey(processInstanceKey).build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.MIGRATED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.CREATED);
  }

  @Test
  void flushedEntityShouldContainMigratedData() {
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .withProcessDefinitionKey(456L)
            .withElementId("elementId")
            .withBpmnProcessId("bpmnProcessId")
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.MIGRATED)
                    .withKey(111)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("111");
    underTest.updateEntity(jobRecord, taskEntity);

    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.PROCESS_DEFINITION_ID, taskEntity.getProcessDefinitionId());
    expectedUpdates.put(TaskTemplate.BPMN_PROCESS_ID, taskEntity.getBpmnProcessId());
    expectedUpdates.put(TaskTemplate.FLOW_NODE_BPMN_ID, taskEntity.getFlowNodeBpmnId());
    expectedUpdates.put(TaskTemplate.STATE, TaskState.CREATED);

    // then
    assertThat(taskEntity.getProcessDefinitionId())
        .isEqualTo(String.valueOf(jobRecordValue.getProcessDefinitionKey()));
    assertThat(taskEntity.getFlowNodeBpmnId()).isEqualTo(jobRecordValue.getElementId());
    assertThat(taskEntity.getBpmnProcessId()).isEqualTo(jobRecordValue.getBpmnProcessId());
    verify(mockRequest, times(1))
        .upsertWithRouting(
            index, taskEntity.getId(), taskEntity, expectedUpdates, taskEntity.getId());
  }

  @Test
  void flushedEntityShouldContainCompletionUpdate() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder().withProcessInstanceKey(processInstanceKey).build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.COMPLETED)
                    .withValue(jobRecordValue)
                    .withKey(111)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("111");
    underTest.updateEntity(jobRecord, taskEntity);

    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.STATE, TaskState.COMPLETED);
    expectedUpdates.put(TaskTemplate.COMPLETION_TIME, taskEntity.getCompletionTime());

    verify(mockRequest, times(1))
        .upsertWithRouting(
            index, taskEntity.getId(), taskEntity, expectedUpdates, taskEntity.getId());
  }

  @Test
  void shouldUpdateEntityFromRecordForFailedWithoutRetriesIntent() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .withRetries(0)
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.FAILED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.FAILED);
  }

  @Test
  void flushedEntityShouldContainFailedWithoutRetriesUpdate() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .withRetries(0)
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.FAILED)
                    .withKey(111)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("111");
    underTest.updateEntity(jobRecord, taskEntity);

    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.STATE, TaskState.FAILED);

    verify(mockRequest, times(1))
        .upsertWithRouting(
            index, taskEntity.getId(), taskEntity, expectedUpdates, taskEntity.getId());
  }

  @Test
  void shouldUpdateEntityFromRecordForFailedWithRetryBackoffIntent() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .withRetries(1)
            .withRetryBackoff(100L)
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.FAILED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.FAILED);
  }

  @Test
  void flushedEntityShouldContainFailedWithRetryBackoffUpdate() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .withRetries(1)
            .withRetryBackoff(100L)
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.FAILED)
                    .withKey(111)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("111");
    underTest.updateEntity(jobRecord, taskEntity);

    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.STATE, TaskState.FAILED);

    verify(mockRequest, times(1))
        .upsertWithRouting(
            index, taskEntity.getId(), taskEntity, expectedUpdates, taskEntity.getId());
  }

  @Test
  void shouldUpdateEntityFromRecordForFailedWithoutRetryBackoffIntent() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .withRetries(1)
            .withRetryBackoff(0L)
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.FAILED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.CREATED);
  }

  @Test
  void flushedEntityShouldContainFailedWithoutRetryBackoffUpdate() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .withRetries(1)
            .withRetryBackoff(0L)
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.FAILED)
                    .withKey(111)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("111");
    underTest.updateEntity(jobRecord, taskEntity);

    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.STATE, TaskState.CREATED);

    verify(mockRequest, times(1))
        .upsertWithRouting(
            index, taskEntity.getId(), taskEntity, expectedUpdates, taskEntity.getId());
  }

  @Test
  void shouldUpdateEntityFromRecordForRecurredAfterBackoffIntent() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder().withProcessInstanceKey(processInstanceKey).build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.RECURRED_AFTER_BACKOFF)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.CREATED);
  }

  @Test
  void shouldDeleteLegacyDocumentOnMigrationCancelForPreviousVersionRecord() {
    // given
    final long firstUserTaskKey = 100;
    final long legacyJobKey = 90; // below watermark → legacy doc _id = jobKey
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.JOB_WORKER, firstUserTaskKey);

    final JobRecordValue migrationCancelValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType(Protocol.USER_TASK_JOB_TYPE)
            .withJobToUserTaskMigration(true)
            .build();

    final Record<JobRecordValue> migrationCancelRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.CANCELED)
                    .withKey(legacyJobKey)
                    .withValue(migrationCancelValue));

    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when: process the migration cancel through the handler
    assertThat(underTest.handlesRecord(migrationCancelRecord)).isTrue();
    final var ids = underTest.generateIds(migrationCancelRecord);
    ids.forEach(
        id -> {
          final var entity = underTest.createNewEntity(id);
          underTest.updateEntity(migrationCancelRecord, entity);
          underTest.flush(index, entity, mockRequest);
        });

    // then — the stale legacy task doc (keyed by jobKey) must be deleted; no upsert should occur
    verify(mockRequest, times(1))
        .deleteWithRouting(index, String.valueOf(legacyJobKey), String.valueOf(legacyJobKey));
    verify(mockRequest, never())
        .upsertWithRouting(
            org.mockito.ArgumentMatchers.eq(index),
            org.mockito.ArgumentMatchers.eq(String.valueOf(legacyJobKey)),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(String.valueOf(legacyJobKey)));
  }

  @Test
  void shouldDeleteLegacyDocumentOnMigrationCancelEvenWhenCreatedInSameBatch() {
    // given: simulates a batch containing both JOB.CREATED and JOB.CANCELED+migration for the
    // same legacy job key — the CANCELED must still issue a delete, not an upsert
    final long firstUserTaskKey = 100;
    final long legacyJobKey = 90;
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.JOB_WORKER, firstUserTaskKey);

    final JobRecordValue createdValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType(Protocol.USER_TASK_JOB_TYPE)
            .withJobToUserTaskMigration(false)
            .build();

    final Record<JobRecordValue> createdRecord =
        factory.generateRecord(
            ValueType.JOB,
            r -> r.withIntent(JobIntent.CREATED).withKey(legacyJobKey).withValue(createdValue));

    final JobRecordValue migrationCancelValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType(Protocol.USER_TASK_JOB_TYPE)
            .withJobToUserTaskMigration(true)
            .build();

    final Record<JobRecordValue> migrationCancelRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.CANCELED)
                    .withKey(legacyJobKey)
                    .withValue(migrationCancelValue));

    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);
    final TaskEntity sharedEntity = underTest.createNewEntity(String.valueOf(legacyJobKey));

    // simulate same-batch processing: CREATED first, then CANCELED+migration on the same entity
    underTest.updateEntity(createdRecord, sharedEntity);
    underTest.updateEntity(migrationCancelRecord, sharedEntity);
    underTest.flush(index, sharedEntity, mockRequest);

    // then — the migration-cancel must mark the entity for deletion so the delete path is taken
    assertThat(sharedEntity.isMarkedForDeletion()).isTrue();
    verify(mockRequest, times(1))
        .deleteWithRouting(index, String.valueOf(legacyJobKey), String.valueOf(legacyJobKey));
    verify(mockRequest, never())
        .upsertWithRouting(
            org.mockito.ArgumentMatchers.eq(index),
            org.mockito.ArgumentMatchers.eq(String.valueOf(legacyJobKey)),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(String.valueOf(legacyJobKey)));
  }

  @Test
  void shouldNotDeleteDocumentOnMigrationCancelForCurrentVersionRecord() {
    // given: jobKey above watermark → current-version scheme (_id = elementInstanceKey)
    final long firstUserTaskKey = 100;
    final long currentJobKey =
        110; // above watermark → current-version doc _id = elementInstanceKey
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.JOB_WORKER, firstUserTaskKey);

    final JobRecordValue migrationCancelValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType(Protocol.USER_TASK_JOB_TYPE)
            .withJobToUserTaskMigration(true)
            .build();

    final Record<JobRecordValue> migrationCancelRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.CANCELED)
                    .withKey(currentJobKey)
                    .withValue(migrationCancelValue));

    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when: current-version migration cancel — handlesRecord returns false, nothing dispatched
    assertThat(underTest.handlesRecord(migrationCancelRecord)).isFalse();
  }

  @Test
  void shouldNotDeleteDocumentOnNonMigrationCancel() {
    // given: a normal (non-migration) JOB CANCELED — must never trigger a delete
    final long firstUserTaskKey = 100;
    final long legacyJobKey = 90; // below watermark, but NOT a migration cancel
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.JOB_WORKER, firstUserTaskKey);

    final JobRecordValue normalCancelValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType(Protocol.USER_TASK_JOB_TYPE)
            .withJobToUserTaskMigration(false)
            .build();

    final Record<JobRecordValue> normalCancelRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.CANCELED)
                    .withKey(legacyJobKey)
                    .withValue(normalCancelValue));

    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when: non-migration cancel is handled normally (state update, not delete)
    assertThat(underTest.handlesRecord(normalCancelRecord)).isTrue();
    final var ids = underTest.generateIds(normalCancelRecord);
    ids.forEach(
        id -> {
          final var entity = underTest.createNewEntity(id);
          underTest.updateEntity(normalCancelRecord, entity);
          underTest.flush(index, entity, mockRequest);
        });

    // then — normal cancel issues an upsert (state update), never a delete
    verify(mockRequest, never())
        .deleteWithRouting(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    verify(mockRequest, times(1))
        .upsertWithRouting(
            org.mockito.ArgumentMatchers.eq(index),
            org.mockito.ArgumentMatchers.eq(String.valueOf(legacyJobKey)),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(String.valueOf(legacyJobKey)));
  }

  @Test
  void flushedEntityShouldContainRecurredAfterBackoffUpdate() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder().withProcessInstanceKey(processInstanceKey).build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.RECURRED_AFTER_BACKOFF)
                    .withValue(jobRecordValue)
                    .withKey(111)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("111");
    underTest.updateEntity(jobRecord, taskEntity);

    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.STATE, TaskState.CREATED);

    verify(mockRequest, times(1))
        .upsertWithRouting(
            index, taskEntity.getId(), taskEntity, expectedUpdates, taskEntity.getId());
  }
}

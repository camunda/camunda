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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.cache.TestFormCache;
import io.camunda.exporter.cache.TestProcessCache;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UserTaskCreatingHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-tasklist-creating-task";
  private final TestFormCache formCache = new TestFormCache();
  private final TestProcessCache processCache = new TestProcessCache();
  private final ExporterMetadata exporterMetadata =
      new ExporterMetadata(TestObjectMapper.objectMapper());
  private final UserTaskCreatingHandler underTest =
      new UserTaskCreatingHandler(indexName, formCache, processCache, exporterMetadata);

  @BeforeEach
  void resetMetadata() {
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK, -1);
  }

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.USER_TASK);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(TaskEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<UserTaskRecordValue> userTaskRecord =
        factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(UserTaskIntent.CREATING));
    // when - then
    assertThat(underTest.handlesRecord(userTaskRecord))
        .describedAs("Expect to handle intent %s", UserTaskIntent.CREATING)
        .isTrue();
  }

  @Test
  void shouldGenerateIdForNewVersionReferenceRecord() {
    // given
    /* For 8.7 Ingested records, the recordKey has to be greater than the firstIngestedUserTaskKey */
    final long firstIngestedUserTaskKey = 100;
    final long recordKey = 110;
    final long processInstanceKey = 111;
    final long flowNodeInstanceKey = 211;
    exporterMetadata.setFirstUserTaskKey(
        TaskImplementation.ZEEBE_USER_TASK, firstIngestedUserTaskKey);
    final Record<UserTaskRecordValue> userTaskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.CREATING)
                    .withKey(recordKey)
                    .withValue(
                        ImmutableUserTaskRecordValue.builder()
                            .withProcessInstanceKey(processInstanceKey)
                            .withUserTaskKey(recordKey)
                            .withElementInstanceKey(flowNodeInstanceKey)
                            .build()));

    // when
    final var idList = underTest.generateIds(userTaskRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(flowNodeInstanceKey));
  }

  @Test
  void shouldGenerateIdForPreviousVersionReferenceRecord() {
    // given
    /* For 8.7 Ingested records referencing 8.6 records, the recordKey has to be less than the firstIngestedUserTaskKey */
    final long firstIngestedUserTaskKey = 100;
    final long recordKey = 90;
    final long processInstanceKey = 111;
    final long flowNodeInstanceKey = 211;
    exporterMetadata.setFirstUserTaskKey(
        TaskImplementation.ZEEBE_USER_TASK, firstIngestedUserTaskKey);
    final Record<UserTaskRecordValue> userTaskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.CREATING)
                    .withKey(recordKey)
                    .withValue(
                        ImmutableUserTaskRecordValue.builder()
                            .withProcessInstanceKey(processInstanceKey)
                            .withUserTaskKey(recordKey)
                            .withElementInstanceKey(flowNodeInstanceKey)
                            .build()));

    // when
    final var idList = underTest.generateIds(userTaskRecord);

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
  void shouldAddEntityOnFlushForNewVersionReefrenceRecord() {
    // given
    final long firstIngestedUserTaskKey = 100;
    final long recordKey = 110;
    final long processInstanceKey = 111;
    final long flowNodeInstanceKey = 211;
    exporterMetadata.setFirstUserTaskKey(
        TaskImplementation.ZEEBE_USER_TASK, firstIngestedUserTaskKey);
    final TaskEntity inputEntity =
        new TaskEntity()
            .setId(String.valueOf(flowNodeInstanceKey))
            .setProcessInstanceId(String.valueOf(processInstanceKey))
            .setKey(recordKey)
            .setFlowNodeInstanceId(String.valueOf(flowNodeInstanceKey));
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    final Map<String, Object> updateFieldsMap = new HashMap<>();

    verify(mockRequest, times(1))
        .addWithRouting(indexName, inputEntity, String.valueOf(processInstanceKey));
  }

  @Test
  void shouldAddEntityOnFlushForPreviousVersionReferenceRecord() {
    // given
    final long firstIngestedUserTaskKey = 100;
    final long recordKey = 90;
    final long processInstanceKey = 111;
    final long flowNodeInstanceKey = 211;
    exporterMetadata.setFirstUserTaskKey(
        TaskImplementation.ZEEBE_USER_TASK, firstIngestedUserTaskKey);
    final TaskEntity inputEntity =
        new TaskEntity()
            .setId(String.valueOf(recordKey))
            .setProcessInstanceId(String.valueOf(processInstanceKey))
            .setKey(recordKey)
            .setFlowNodeInstanceId(String.valueOf(flowNodeInstanceKey));
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    final Map<String, Object> updateFieldsMap = new HashMap<>();

    verify(mockRequest, times(1)).addWithRouting(indexName, inputEntity, String.valueOf(recordKey));
  }

  @Test
  void shouldCreateEntityFromRecord() {
    // given
    final long processInstanceKey = 123;
    final long processDefinitionKey = 555;
    final long flowNodeInstanceKey = 456;
    final long recordKey = 110;
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK, 100);
    final var dateTime = OffsetDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    final var formKey = 456L;
    final var elementId = "elementId";
    final var rootProcessInstanceKey = 999L;
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .from(factory.generateObject(UserTaskRecordValue.class))
            .withAssignee("")
            .withChangedAttributes(List.of())
            .withFollowUpDate(dateTime)
            .withDueDate(dateTime)
            .withCandidateGroupsList(Arrays.asList("group1", "group2"))
            .withCandidateUsersList(Arrays.asList("user1", "user2"))
            .withProcessInstanceKey(processInstanceKey)
            .withProcessDefinitionKey(processDefinitionKey)
            .withElementInstanceKey(flowNodeInstanceKey)
            .withFormKey(formKey)
            .withElementId(elementId)
            .withRootProcessInstanceKey(rootProcessInstanceKey)
            .build();

    final Record<UserTaskRecordValue> taskCreatingRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.CREATING)
                    .withKey(recordKey)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    formCache.put(String.valueOf(formKey), new CachedFormEntity("my-form", 987L));
    processCache.put(
        processDefinitionKey,
        new CachedProcessEntity(
            "my-process", 1, "v1", List.of(), Map.of(elementId, "my-flow-node")));

    // when
    final TaskEntity taskEntity =
        new TaskEntity().setId(String.valueOf(taskRecordValue.getElementInstanceKey()));
    underTest.updateEntity(taskCreatingRecord, taskEntity);

    // then
    assertThat(taskEntity.getId())
        .isEqualTo(String.valueOf(taskRecordValue.getElementInstanceKey()));
    assertThat(taskEntity.getKey()).isEqualTo(taskCreatingRecord.getKey());
    assertThat(taskEntity.getTenantId()).isEqualTo(taskRecordValue.getTenantId());
    assertThat(taskEntity.getPartitionId()).isEqualTo(taskCreatingRecord.getPartitionId());
    assertThat(taskEntity.getPosition()).isEqualTo(taskCreatingRecord.getPosition());
    assertThat(taskEntity.getProcessInstanceId()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(taskEntity.getFlowNodeBpmnId()).isEqualTo(taskRecordValue.getElementId());
    assertThat(taskEntity.getName()).isEqualTo("my-flow-node");
    assertThat(taskEntity.getBpmnProcessId()).isEqualTo(taskRecordValue.getBpmnProcessId());
    assertThat(taskEntity.getProcessDefinitionId())
        .isEqualTo(String.valueOf(taskRecordValue.getProcessDefinitionKey()));
    assertThat(taskEntity.getProcessDefinitionVersion())
        .isEqualTo(taskRecordValue.getProcessDefinitionVersion());
    assertThat(taskEntity.getFormKey()).isEqualTo(String.valueOf(taskRecordValue.getFormKey()));
    assertThat(taskEntity.getFormId()).isEqualTo("my-form");
    assertThat(taskEntity.getFormVersion()).isEqualTo(987L);
    assertThat(taskEntity.getExternalFormReference())
        .isEqualTo(taskRecordValue.getExternalFormReference());
    assertThat(taskEntity.getCustomHeaders()).isEqualTo(taskRecordValue.getCustomHeaders());
    assertThat(taskEntity.getPriority()).isEqualTo(taskRecordValue.getPriority());
    assertThat(taskEntity.getAction()).isEqualTo(taskRecordValue.getAction());
    assertThat(taskEntity.getDueDate()).isEqualTo(dateTime);
    assertThat(taskEntity.getFollowUpDate()).isEqualTo(dateTime);
    assertThat(Arrays.stream(taskEntity.getCandidateGroups()).toList())
        .isEqualTo(taskRecordValue.getCandidateGroupsList());
    assertThat(Arrays.stream(taskEntity.getCandidateUsers()).toList())
        .isEqualTo(taskRecordValue.getCandidateUsersList());
    assertThat(taskEntity.getAssignee()).isNull();
    assertThat(taskEntity.getJoin()).isNotNull();
    assertThat(taskEntity.getJoin().getParent()).isEqualTo(processInstanceKey);
    assertThat(taskEntity.getJoin().getName()).isEqualTo(TaskJoinRelationshipType.TASK.getType());
    assertThat(taskEntity.getState()).isEqualTo(TaskState.CREATING);
    assertThat(taskEntity.getCreationTime())
        .isEqualTo(
            ExporterUtil.toZonedOffsetDateTime(
                Instant.ofEpochMilli(taskCreatingRecord.getTimestamp())));
    assertThat(taskEntity.getImplementation()).isEqualTo(TaskImplementation.ZEEBE_USER_TASK);
    assertThat(taskEntity.getRootProcessInstanceKey()).isEqualTo(rootProcessInstanceKey);
  }

  @Test
  public void shouldNotSetRootProcessInstanceKeyWhenUndefined() {
    // given
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder().withRootProcessInstanceKey(-1L).build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r -> r.withIntent(UserTaskIntent.CREATING).withValue(taskRecordValue));

    // when
    final TaskEntity taskEntity = underTest.createNewEntity("id");
    underTest.updateEntity(taskRecord, taskEntity);

    // then
    assertThat(taskEntity.getRootProcessInstanceKey()).isNull();
  }
}

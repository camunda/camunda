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
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.exporter.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.usertask.TaskState;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UserTaskHandlerTest {
  private static final Set<UserTaskIntent> SUPPORTED_INTENTS =
      EnumSet.of(
          UserTaskIntent.CREATED,
          UserTaskIntent.COMPLETED,
          UserTaskIntent.CANCELED,
          UserTaskIntent.MIGRATED,
          UserTaskIntent.ASSIGNED,
          UserTaskIntent.UPDATED);

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-tasklist-task";
  private final TestFormCache formCache = new TestFormCache();
  private final ExporterMetadata exporterMetadata =
      new ExporterMetadata(TestObjectMapper.objectMapper());
  private final UserTaskHandler underTest =
      new UserTaskHandler(indexName, formCache, exporterMetadata);

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
    SUPPORTED_INTENTS.forEach(
        intent -> {
          final Record<UserTaskRecordValue> userTaskRecord =
              factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(intent));
          // when - then
          assertThat(underTest.handlesRecord(userTaskRecord)).isTrue();
        });
  }

  @Test
  void shouldNotHandleRecord() {
    // given
    Arrays.stream(UserTaskIntent.values())
        .filter(intent -> !SUPPORTED_INTENTS.contains(intent))
        .forEach(
            intent -> {
              final Record<UserTaskRecordValue> variableRecord =
                  factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(intent));
              // when - then
              assertThat(underTest.handlesRecord(variableRecord)).isFalse();
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
    exporterMetadata.setFirstUserTaskKey(
        TaskImplementation.ZEEBE_USER_TASK, firstIngestedUserTaskKey);
    final Record<UserTaskRecordValue> userTaskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.CREATED)
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
                r.withIntent(UserTaskIntent.CREATED)
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
  void shouldAddEntityOnFlushForNewVersionReferenceRecord() {
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
        .upsertWithRouting(
            indexName,
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

    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName,
            String.valueOf(recordKey),
            inputEntity,
            updateFieldsMap,
            String.valueOf(recordKey));
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final long processInstanceKey = 123;
    final long flowNodeInstanceKey = 456;
    final long recordKey = 110;
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK, 100);
    final var dateTime = OffsetDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    final var formKey = 456L;
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .from(factory.generateObject(UserTaskRecordValue.class))
            .withFollowUpDate(dateTime)
            .withDueDate(dateTime)
            .withCandidateGroupsList(Arrays.asList("group1", "group2"))
            .withCandidateUsersList(Arrays.asList("user1", "user2"))
            .withProcessInstanceKey(processInstanceKey)
            .withElementInstanceKey(flowNodeInstanceKey)
            .withFormKey(formKey)
            .build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.CREATED)
                    .withKey(recordKey)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    formCache.put(String.valueOf(formKey), new CachedFormEntity("my-form", 987L));

    // when
    final TaskEntity taskEntity =
        new TaskEntity().setId(String.valueOf(taskRecordValue.getElementInstanceKey()));
    underTest.updateEntity(taskRecord, taskEntity);

    // then
    assertThat(taskEntity.getId())
        .isEqualTo(String.valueOf(taskRecordValue.getElementInstanceKey()));
    assertThat(taskEntity.getKey()).isEqualTo(taskRecord.getKey());
    assertThat(taskEntity.getTenantId()).isEqualTo(taskRecordValue.getTenantId());
    assertThat(taskEntity.getPartitionId()).isEqualTo(taskRecord.getPartitionId());
    assertThat(taskEntity.getPosition()).isEqualTo(taskRecord.getPosition());
    assertThat(taskEntity.getProcessInstanceId()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(taskEntity.getFlowNodeBpmnId()).isEqualTo(taskRecordValue.getElementId());
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
    assertThat(taskEntity.getAssignee()).isEqualTo(taskRecordValue.getAssignee());
    assertThat(taskEntity.getJoin()).isNotNull();
    assertThat(taskEntity.getJoin().getParent()).isEqualTo(processInstanceKey);
    assertThat(taskEntity.getJoin().getName()).isEqualTo(TaskJoinRelationshipType.TASK.getType());
    assertThat(taskEntity.getState()).isEqualTo(TaskState.CREATED);
    assertThat(taskEntity.getCreationTime())
        .isEqualTo(
            ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(taskRecord.getTimestamp())));
    assertThat(taskEntity.getImplementation()).isEqualTo(TaskImplementation.ZEEBE_USER_TASK);
  }

  @Test
  void shouldUpdateEntityFromRecordForCanceledIntent() {
    // given
    final long processInstanceKey = 123;
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .from(factory.generateObject(UserTaskRecordValue.class))
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.CANCELED)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(taskRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.CANCELED);
    assertThat(taskEntity.getCompletionTime())
        .isEqualTo(
            ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(taskRecord.getTimestamp())));
  }

  @Test
  void shouldUpdateEntityFromRecordForAssignedIntent() {
    // given
    final long processInstanceKey = 123;
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withProcessInstanceKey(processInstanceKey)
            .withAssignee("provided_assignee")
            .withChangedAttributes(List.of("assignee"))
            .build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.ASSIGNED)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = underTest.createNewEntity("id").setState(TaskState.CREATED);
    underTest.updateEntity(taskRecord, taskEntity);

    // then
    assertThat(taskEntity)
        .describedAs("Expected task entity to have provided assignee")
        .satisfies(
            entity -> {
              assertThat(entity.getAssignee()).isEqualTo("provided_assignee");
              assertThat(entity.getChangedAttributes()).containsOnly("assignee");
              assertThat(entity.getState()).isEqualTo(TaskState.CREATED);
            })
        .describedAs(
            "Expected not changed user task record fields to have `null` values in task entity")
        .extracting(
            TaskEntity::getDueDate,
            TaskEntity::getFollowUpDate,
            TaskEntity::getPriority,
            TaskEntity::getCandidateGroups,
            TaskEntity::getCandidateUsers)
        .containsOnlyNulls();
  }

  @Test
  void shouldResetEntityAssigneeOnTaskUnassigning() {
    // given
    final long processInstanceKey = 123;
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withProcessInstanceKey(processInstanceKey)
            .withAssignee("")
            .withChangedAttributes(List.of("assignee"))
            .build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.ASSIGNED)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity =
        underTest
            .createNewEntity("id")
            .setAssignee("existing_assignee")
            .setState(TaskState.CREATED);
    underTest.updateEntity(taskRecord, taskEntity);

    // then
    assertThat(taskEntity)
        .describedAs("Expected task entity to contain `null` as assignee after task unassigning")
        .satisfies(
            entity -> {
              assertThat(entity.getAssignee()).isNull();
              assertThat(entity.getChangedAttributes()).containsOnly("assignee");
            });
  }

  @Test
  void shouldUpdateEntityFromRecordForAssignedIntentWithCorrectedData() {
    // given
    final long processInstanceKey = 123;
    final var dueDateTime =
        OffsetDateTime.now().plusDays(2).format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    final var followUpDateTime = OffsetDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withProcessInstanceKey(processInstanceKey)
            // corrected data
            .withAssignee("corrected_assignee")
            .withDueDate(dueDateTime)
            .withFollowUpDate(followUpDateTime)
            .withPriority(88)
            .withCandidateGroupsList(List.of("corrected_group1"))
            .withCandidateUsersList(List.of("corrected_user1", "corrected_user2"))
            .withChangedAttributes(
                List.of(
                    "assignee",
                    "dueDate",
                    "followUpDate",
                    "priority",
                    "candidateGroupsList",
                    "candidateUsersList"))
            .build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.ASSIGNED)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity =
        underTest
            .createNewEntity("id")
            .setAssignee("existing_assignee")
            .setState(TaskState.CREATED);
    underTest.updateEntity(taskRecord, taskEntity);

    // then
    assertThat(taskEntity)
        .describedAs("Expected task entity to contain corrected data from user task record")
        .satisfies(
            entity -> {
              assertThat(entity.getAssignee()).isEqualTo("corrected_assignee");
              assertThat(entity.getDueDate()).isEqualTo(dueDateTime);
              assertThat(entity.getFollowUpDate()).isEqualTo(followUpDateTime);
              assertThat(entity.getPriority()).isEqualTo(88);
              assertThat(entity.getCandidateGroups()).contains("corrected_group1");
              assertThat(entity.getCandidateUsers()).contains("corrected_user1", "corrected_user2");
              assertThat(entity.getChangedAttributes())
                  .containsOnly(
                      "assignee",
                      "dueDate",
                      "followUpDate",
                      "priority",
                      "candidateGroupsList",
                      "candidateUsersList");
            });
  }

  @Test
  void shouldUpdateEntityFromRecordForCompletedIntent() {
    // given
    final long processInstanceKey = 123;
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder().withProcessInstanceKey(processInstanceKey).build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.COMPLETED)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = underTest.createNewEntity("id");
    underTest.updateEntity(taskRecord, taskEntity);

    // then
    final var expectedCompletionTime =
        ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(taskRecord.getTimestamp()));
    assertThat(taskEntity)
        .describedAs("Expected task entity to have COMPLETED state and timestamp")
        .satisfies(
            entity -> {
              assertThat(entity.getState()).isEqualTo(TaskState.COMPLETED);
              assertThat(entity.getCompletionTime()).isEqualTo(expectedCompletionTime);
              assertThat(entity.getChangedAttributes()).isEmpty();
            })
        .describedAs(
            "Expected not changed user task record fields to have `null` values in task entity")
        .extracting(
            TaskEntity::getAssignee,
            TaskEntity::getDueDate,
            TaskEntity::getFollowUpDate,
            TaskEntity::getPriority,
            TaskEntity::getCandidateGroups,
            TaskEntity::getCandidateUsers)
        .containsOnlyNulls();
  }

  @Test
  void shouldUpdateEntityFromRecordForCompletedIntentWithCorrectedData() {
    // given
    final long processInstanceKey = 1234;
    final var dueDateTime =
        OffsetDateTime.now().plusDays(2).format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    final var followUpDateTime = OffsetDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .from(factory.generateObject(UserTaskRecordValue.class))
            .withProcessInstanceKey(processInstanceKey)
            // corrected data
            .withAssignee("corrected_assignee")
            .withDueDate(dueDateTime)
            .withFollowUpDate(followUpDateTime)
            .withPriority(22)
            .withCandidateGroupsList(List.of("corrected_group1"))
            .withCandidateUsersList(List.of("corrected_user1", "corrected_user2"))
            .withChangedAttributes(
                List.of(
                    "assignee",
                    "dueDate",
                    "followUpDate",
                    "priority",
                    "candidateGroupsList",
                    "candidateUsersList"))
            .build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.COMPLETED)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = underTest.createNewEntity("id");
    underTest.updateEntity(taskRecord, taskEntity);

    // then
    assertThat(taskEntity)
        .describedAs("Expected task entity to contain corrected data from user task record")
        .satisfies(
            entity -> {
              assertThat(entity.getAssignee()).isEqualTo("corrected_assignee");
              assertThat(entity.getDueDate()).isEqualTo(dueDateTime);
              assertThat(entity.getFollowUpDate()).isEqualTo(followUpDateTime);
              assertThat(entity.getPriority()).isEqualTo(22);
              assertThat(entity.getCandidateGroups()).contains("corrected_group1");
              assertThat(entity.getCandidateUsers()).contains("corrected_user1", "corrected_user2");
              assertThat(entity.getChangedAttributes())
                  .containsOnly(
                      "assignee",
                      "dueDate",
                      "followUpDate",
                      "priority",
                      "candidateGroupsList",
                      "candidateUsersList");
            })
        .describedAs("Expected task entity to contain updated completion fields")
        .satisfies(
            entity -> {
              assertThat(entity.getState()).isEqualTo(TaskState.COMPLETED);
              assertThat(entity.getCompletionTime())
                  .isEqualTo(
                      ExporterUtil.toZonedOffsetDateTime(
                          Instant.ofEpochMilli(taskRecord.getTimestamp())));
            });
  }

  @Test
  void shouldUpdateEntityFromRecordForMigratedIntent() {
    // given
    final long processInstanceKey = 123;
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder().withProcessInstanceKey(processInstanceKey).build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.MIGRATED)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(taskRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.CREATED);
  }

  @Test
  void flushedEntityShouldContainMigratedData() {
    final long processInstanceKey = 123;
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withProcessDefinitionKey(456L)
            .withElementId("elementId")
            .withBpmnProcessId("bpmnProcessId")
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.MIGRATED)
                    .withKey(111)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId(String.valueOf(111L));
    underTest.updateEntity(taskRecord, taskEntity);

    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.PROCESS_DEFINITION_ID, taskEntity.getProcessDefinitionId());
    expectedUpdates.put(TaskTemplate.BPMN_PROCESS_ID, taskEntity.getBpmnProcessId());
    expectedUpdates.put(TaskTemplate.FLOW_NODE_BPMN_ID, taskEntity.getFlowNodeBpmnId());
    expectedUpdates.put(TaskTemplate.STATE, TaskState.CREATED);

    // then
    assertThat(taskEntity.getProcessDefinitionId())
        .isEqualTo(String.valueOf(taskRecordValue.getProcessDefinitionKey()));
    assertThat(taskEntity.getFlowNodeBpmnId()).isEqualTo(taskRecordValue.getElementId());
    assertThat(taskEntity.getBpmnProcessId()).isEqualTo(taskRecordValue.getBpmnProcessId());
    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName, taskEntity.getId(), taskEntity, expectedUpdates, taskEntity.getId());
  }

  @Test
  void flushedEntityShouldContainAssignee() {
    final long processInstanceKey = 123;
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withAssignee("test-assignee")
            .withProcessInstanceKey(processInstanceKey)
            .withChangedAttributes(List.of("assignee"))
            .build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.ASSIGNED)
                    .withKey(123)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = underTest.createNewEntity(String.valueOf(123));
    underTest.updateEntity(taskRecord, taskEntity);

    final BatchRequest mockRequest = mock(BatchRequest.class);

    underTest.flush(taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.ASSIGNEE, taskEntity.getAssignee());
    expectedUpdates.put(TaskTemplate.CHANGED_ATTRIBUTES, List.of("assignee"));

    // then
    assertThat(taskEntity.getAssignee()).isEqualTo(taskRecordValue.getAssignee());
    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName,
            taskEntity.getId(),
            taskEntity,
            expectedUpdates,
            taskEntity.getProcessInstanceId());
  }

  @Test
  void flushedEntityShouldContainAssigneeOnUnassign() {
    final long processInstanceKey = 123;
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withAssignee("")
            .withProcessInstanceKey(processInstanceKey)
            .withChangedAttributes(List.of("assignee"))
            .build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.ASSIGNED)
                    .withKey(123)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity =
        underTest.createNewEntity(String.valueOf(123)).setAssignee("test-assignee");
    underTest.updateEntity(taskRecord, taskEntity);

    final BatchRequest mockRequest = mock(BatchRequest.class);

    underTest.flush(taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.ASSIGNEE, null);
    expectedUpdates.put(TaskTemplate.CHANGED_ATTRIBUTES, List.of("assignee"));

    // then
    assertThat(taskEntity.getAssignee()).isEqualTo(null);
    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName,
            taskEntity.getId(),
            taskEntity,
            expectedUpdates,
            taskEntity.getProcessInstanceId());
  }

  @Test
  void flushedEntityShouldContainCompletionUpdate() {
    // given
    final long processInstanceKey = 123;
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder().withProcessInstanceKey(processInstanceKey).build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.COMPLETED)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis())
                    .withKey(123));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId(String.valueOf(123));
    underTest.updateEntity(taskRecord, taskEntity);

    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.STATE, TaskState.COMPLETED);
    expectedUpdates.put(TaskTemplate.COMPLETION_TIME, taskEntity.getCompletionTime());

    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName,
            taskEntity.getId(),
            taskEntity,
            expectedUpdates,
            taskEntity.getProcessInstanceId());
  }

  @Test
  void flushedEntityShouldContainUpdates() {
    // given
    final long processInstanceKey = 123;
    final long flowNodeInstanceKey = 456;
    final long recordKey = 110;
    exporterMetadata.setFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK, 100);
    final var dateTime = OffsetDateTime.now();
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withChangedAttributes(
                List.of(
                    "priority",
                    "dueDate",
                    "followUpDate",
                    "candidateUsersList",
                    "candidateGroupsList"))
            .withProcessInstanceKey(processInstanceKey)
            .withPriority(99)
            .withDueDate(dateTime.format(DateTimeFormatter.ISO_ZONED_DATE_TIME))
            .withFollowUpDate(dateTime.format(DateTimeFormatter.ISO_ZONED_DATE_TIME))
            .withCandidateUsersList(Arrays.asList("user1", "user2"))
            .withCandidateGroupsList(Arrays.asList("group1", "group2"))
            .withElementInstanceKey(flowNodeInstanceKey)
            .build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.UPDATED)
                    .withValue(taskRecordValue)
                    .withKey(recordKey)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = underTest.createNewEntity(String.valueOf(recordKey));
    underTest.updateEntity(taskRecord, taskEntity);

    final BatchRequest mockRequest = mock(BatchRequest.class);

    underTest.flush(taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.PRIORITY, taskEntity.getPriority());
    expectedUpdates.put(TaskTemplate.FOLLOW_UP_DATE, taskEntity.getFollowUpDate());
    expectedUpdates.put(TaskTemplate.DUE_DATE, taskEntity.getDueDate());
    expectedUpdates.put(TaskTemplate.CANDIDATE_USERS, taskEntity.getCandidateUsers());
    expectedUpdates.put(TaskTemplate.CANDIDATE_GROUPS, taskEntity.getCandidateGroups());
    expectedUpdates.put(
        TaskTemplate.CHANGED_ATTRIBUTES,
        List.of(
            "priority", "dueDate", "followUpDate", "candidateUsersList", "candidateGroupsList"));

    // then
    assertThat(taskEntity.getPriority()).isEqualTo(taskRecordValue.getPriority());
    assertThat(taskEntity.getDueDate()).isEqualTo(taskRecordValue.getDueDate());
    assertThat(taskEntity.getFollowUpDate()).isEqualTo(taskRecordValue.getFollowUpDate());
    assertThat(Arrays.stream(taskEntity.getCandidateGroups()).toList())
        .isEqualTo(taskRecordValue.getCandidateGroupsList());
    assertThat(Arrays.stream(taskEntity.getCandidateUsers()).toList())
        .isEqualTo(taskRecordValue.getCandidateUsersList());
    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName,
            String.valueOf(recordKey),
            taskEntity,
            expectedUpdates,
            String.valueOf(processInstanceKey));
  }

  @Test
  void flushedEntityShouldContainSequentialUpdatesInTheSameBatch() {
    final long processInstanceKey = 123;
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withChangedAttributes(List.of("priority"))
            .withProcessInstanceKey(processInstanceKey)
            .withPriority(99)
            .build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.UPDATED)
                    .withKey(111)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    final UserTaskRecordValue assignTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withAssignee("test-assignee")
            .withProcessInstanceKey(processInstanceKey)
            .withChangedAttributes(List.of("assignee"))
            .build();

    final Record<UserTaskRecordValue> assignTaskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.ASSIGNED)
                    .withKey(111)
                    .withValue(assignTaskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = underTest.createNewEntity(String.valueOf(111));
    underTest.updateEntity(taskRecord, taskEntity);
    underTest.updateEntity(assignTaskRecord, taskEntity);

    final BatchRequest mockRequest = mock(BatchRequest.class);

    underTest.flush(taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.PRIORITY, taskEntity.getPriority());
    expectedUpdates.put(TaskTemplate.ASSIGNEE, taskEntity.getAssignee());
    expectedUpdates.put(TaskTemplate.CHANGED_ATTRIBUTES, List.of("priority", "assignee"));

    // then
    assertThat(taskEntity.getPriority()).isEqualTo(taskRecordValue.getPriority());
    assertThat(taskEntity.getAssignee()).isEqualTo(assignTaskRecordValue.getAssignee());
    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName, taskEntity.getId(), taskEntity, expectedUpdates, taskEntity.getId());
  }
}

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

import io.camunda.exporter.cache.TestFormCache;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.tasklist.TaskState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
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
  private final UserTaskHandler underTest = new UserTaskHandler(indexName, formCache);

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
          final Record<UserTaskRecordValue> variableRecord =
              factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(intent));
          // when - then
          assertThat(underTest.handlesRecord(variableRecord)).isTrue();
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
  void shouldGenerateIds() {
    // given
    final long expectedId = 123;
    final Record<UserTaskRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(VariableIntent.CREATED)
                    .withValue(
                        ImmutableUserTaskRecordValue.builder()
                            .withElementInstanceKey(expectedId)
                            .build()));

    // when
    final var idList = underTest.generateIds(processInstanceRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(expectedId));
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
  void shouldAddEntityOnFlush() {
    // given
    final TaskEntity inputEntity = new TaskEntity().setProcessInstanceId("111");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    final Map<String, Object> updateFieldsMap = new HashMap<>();

    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName,
            inputEntity.getId(),
            inputEntity,
            updateFieldsMap,
            inputEntity.getProcessInstanceId());
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final var dateTime = OffsetDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    final var processInstanceKey = 123L;
    final var formKey = 456L;
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .from(factory.generateObject(UserTaskRecordValue.class))
            .withFollowUpDate(dateTime)
            .withDueDate(dateTime)
            .withCandidateGroupsList(Arrays.asList("group1", "group2"))
            .withCandidateUsersList(Arrays.asList("user1", "user2"))
            .withProcessInstanceKey(processInstanceKey)
            .withFormKey(formKey)
            .build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.CREATED)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    formCache.put(String.valueOf(formKey), new CachedFormEntity("my-form", 987L));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
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
    assertThat(taskEntity.getJoin().getParent()).isEqualTo(taskRecordValue.getProcessInstanceKey());
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
  void shouldUpdateEntityFromRecordForCompletedIntent() {
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
                r.withIntent(UserTaskIntent.COMPLETED)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(taskRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.COMPLETED);
    assertThat(taskEntity.getCompletionTime())
        .isEqualTo(
            ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(taskRecord.getTimestamp())));
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
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
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
            indexName,
            taskEntity.getId(),
            taskEntity,
            expectedUpdates,
            taskEntity.getProcessInstanceId());
  }

  @Test
  void flushedEntityShouldContainAssignee() {
    final long processInstanceKey = 123;
    final UserTaskRecordValue taskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withAssignee("test-assignee")
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.ASSIGNED)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = underTest.createNewEntity("id");
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
            .build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.ASSIGNED)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = underTest.createNewEntity("id").setAssignee("test-assignee");
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
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
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
    final var dateTime = OffsetDateTime.now();
    final long processInstanceKey = 123;
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
            .build();

    final Record<UserTaskRecordValue> taskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.UPDATED)
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = underTest.createNewEntity("id");
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
            taskEntity.getId(),
            taskEntity,
            expectedUpdates,
            taskEntity.getProcessInstanceId());
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
                    .withValue(taskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    final UserTaskRecordValue assignTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withAssignee("test-assignee")
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<UserTaskRecordValue> assignTaskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.ASSIGNED)
                    .withValue(assignTaskRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = underTest.createNewEntity("id");
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
            indexName,
            taskEntity.getId(),
            taskEntity,
            expectedUpdates,
            taskEntity.getProcessInstanceId());
  }
}

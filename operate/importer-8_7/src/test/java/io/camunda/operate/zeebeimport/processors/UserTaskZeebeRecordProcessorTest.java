/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.processors;

import static io.camunda.webapps.schema.entities.AbstractExporterEntity.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.operate.template.UserTaskTemplate;
import io.camunda.webapps.schema.entities.operate.UserTaskEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTaskZeebeRecordProcessorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private UserTaskTemplate userTaskTemplate;
  private UserTaskZeebeRecordProcessor userTaskZeebeRecordProcessor;

  @BeforeEach
  void setUp() {
    userTaskZeebeRecordProcessor = new UserTaskZeebeRecordProcessor(userTaskTemplate, objectMapper);
    assertThat(userTaskZeebeRecordProcessor).isNotNull();
  }

  @Test
  void createdEvent() throws PersistenceException {
    /* given */
    final var batchRequest = mock(BatchRequest.class);
    final var userTaskRecord = (Record<UserTaskRecordValue>) mock(Record.class);
    final var userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(1L)
            .withTenantId(DEFAULT_TENANT_ID)
            .withPriority(50)
            .build();
    when(userTaskTemplate.getFullQualifiedName()).thenReturn("user-task-index");
    when(userTaskRecord.getIntent()).thenReturn(UserTaskIntent.CREATED);
    when(userTaskRecord.getValue()).thenReturn(userTaskRecordValue);
    /* when */
    userTaskZeebeRecordProcessor.processUserTaskRecord(batchRequest, userTaskRecord);
    /* then */
    final var expectedUserEntity =
        new UserTaskEntity()
            .setId("1")
            .setKey(1L)
            .setUserTaskKey(1L)
            .setVariables("{}")
            .setCandidateUsers(List.of())
            .setCandidateGroups(List.of())
            .setFormKey(0L)
            .setElementInstanceKey(0L)
            .setProcessDefinitionKey(0L)
            .setProcessDefinitionVersion(0)
            .setProcessInstanceKey(0L)
            .setTenantId(DEFAULT_TENANT_ID)
            .setPriority(50)
            .setChangedAttributes(List.of());
    verify(batchRequest).addWithId("user-task-index", "1", expectedUserEntity);
  }

  @Test
  void migratedEvent() throws PersistenceException {
    /* given */
    final var batchRequest = mock(BatchRequest.class);
    final var userTaskRecord = (Record<UserTaskRecordValue>) mock(Record.class);
    final var userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(1L)
            .withTenantId(DEFAULT_TENANT_ID)
            .withBpmnProcessId("bpmn process id")
            .withProcessDefinitionVersion(2)
            .withProcessDefinitionKey(1L)
            .withElementId("element id")
            .build();
    when(userTaskTemplate.getFullQualifiedName()).thenReturn("user-task-index");
    when(userTaskRecord.getIntent()).thenReturn(UserTaskIntent.MIGRATED);
    when(userTaskRecord.getValue()).thenReturn(userTaskRecordValue);
    /* when */
    userTaskZeebeRecordProcessor.processUserTaskRecord(batchRequest, userTaskRecord);
    /* then */
    final Map<String, Object> expectedUpdateFields =
        Map.of(
            "bpmnProcessId",
            "bpmn process id",
            "processDefinitionVersion",
            2,
            "processDefinitionKey",
            1L,
            "elementId",
            "element id");
    verifyUpsert(batchRequest, expectedUpdateFields);
  }

  @Test
  void assignedEvent() throws PersistenceException {
    /* given */
    final var batchRequest = mock(BatchRequest.class);
    final var userTaskRecord = (Record<UserTaskRecordValue>) mock(Record.class);
    final var userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(1L)
            .withTenantId(DEFAULT_TENANT_ID)
            .withAssignee("Homer Simpson")
            .withAction("assign")
            .build();
    when(userTaskTemplate.getFullQualifiedName()).thenReturn("user-task-index");
    when(userTaskRecord.getIntent()).thenReturn(UserTaskIntent.ASSIGNED);
    when(userTaskRecord.getValue()).thenReturn(userTaskRecordValue);
    /* when */
    userTaskZeebeRecordProcessor.processUserTaskRecord(batchRequest, userTaskRecord);
    /* then */
    final Map<String, Object> expectedUpdateFields =
        Map.of("assignee", "Homer Simpson", "action", "assign");
    verifyUpsert(batchRequest, expectedUpdateFields);
  }

  @Test
  // Unassigned is implemented as assigned event with an empty assignee
  void unassignedEvent() throws PersistenceException {
    /* given */
    final var batchRequest = mock(BatchRequest.class);
    final var userTaskRecord = (Record<UserTaskRecordValue>) mock(Record.class);
    final var userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(1L)
            .withTenantId(DEFAULT_TENANT_ID)
            .withAssignee("")
            .withAction("assign")
            .build();
    when(userTaskTemplate.getFullQualifiedName()).thenReturn("user-task-index");
    when(userTaskRecord.getIntent()).thenReturn(UserTaskIntent.ASSIGNED);
    when(userTaskRecord.getValue()).thenReturn(userTaskRecordValue);
    /* when */
    userTaskZeebeRecordProcessor.processUserTaskRecord(batchRequest, userTaskRecord);
    /* then */
    final Map<String, Object> expectedUpdateFields = Map.of("assignee", "", "action", "assign");
    verifyUpsert(batchRequest, expectedUpdateFields);
  }

  void updatedEvent() throws PersistenceException {
    /* given */
    final var batchRequest = mock(BatchRequest.class);
    final var userTaskRecord = (Record<UserTaskRecordValue>) mock(Record.class);
    final var userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(1L)
            .withTenantId(DEFAULT_TENANT_ID)
            .withCandidateUsersList(List.of("Homer", "Marge"))
            .withCandidateGroupsList(List.of("Simpsons", "Flanders"))
            .withDueDate("2023-05-23T01:02:03+01:00")
            .withAction("update")
            .withChangedAttributes(List.of("candidateUserList", "candidateGroupList", "dueDate"))
            .build();
    when(userTaskTemplate.getFullQualifiedName()).thenReturn("user-task-index");
    when(userTaskRecord.getIntent()).thenReturn(UserTaskIntent.UPDATED);
    when(userTaskRecord.getValue()).thenReturn(userTaskRecordValue);
    /* when */
    userTaskZeebeRecordProcessor.processUserTaskRecord(batchRequest, userTaskRecord);
    /* then */
    final Map<String, Object> expectedUpdateFields =
        Map.of(
            "candidateUsers",
            List.of("Homer", "Marge"),
            "candidateGroups",
            List.of("Simpsons", "Flanders"),
            "dueDate",
            OffsetDateTime.parse("2023-05-23T01:02:03+01:00"),
            "action",
            "update",
            "changedAttributes",
            List.of("candidateUserList", "candidateGroupList", "dueDate"));
    verifyUpsert(batchRequest, expectedUpdateFields);
  }

  @Test // https://github.com/camunda/camunda/issues/17611
  void updateShouldIgnoreMissingAttributes() throws PersistenceException {
    /* given */
    final var batchRequest = mock(BatchRequest.class);
    final var userTaskRecord = (Record<UserTaskRecordValue>) mock(Record.class);
    final var userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(1L)
            .withTenantId(DEFAULT_TENANT_ID)
            .withCandidateUsersList(List.of("string"))
            .withCandidateGroupsList(List.of("string"))
            .withDueDate("2024-04-03T20:22:18.305Z")
            .withFollowUpDate("2024-04-03T20:22:18.305Z")
            .withAction("update")
            .withChangedAttributes(
                List.of(
                    "candidateUsersList",
                    "candidateGroupsList",
                    "dueDate",
                    "followUpDate",
                    "attribute-not-exist"))
            .build();
    when(userTaskTemplate.getFullQualifiedName()).thenReturn("user-task-index");
    when(userTaskRecord.getIntent()).thenReturn(UserTaskIntent.UPDATED);
    when(userTaskRecord.getValue()).thenReturn(userTaskRecordValue);
    /* when */
    userTaskZeebeRecordProcessor.processUserTaskRecord(batchRequest, userTaskRecord);
    /* then */
    final Map<String, Object> expectedUpdateFields =
        Map.of(
            "candidateUsers",
            List.of("string"),
            "candidateGroups",
            List.of("string"),
            "dueDate",
            OffsetDateTime.parse("2024-04-03T20:22:18.305Z"),
            "followUpDate",
            OffsetDateTime.parse("2024-04-03T20:22:18.305Z"),
            "action",
            "update",
            "changedAttributes",
            List.of(
                "candidateUsersList",
                "candidateGroupsList",
                "dueDate",
                "followUpDate",
                "attribute-not-exist"));
    verifyUpsert(batchRequest, expectedUpdateFields);
  }

  @Test
  void completedEvent() throws PersistenceException, JsonProcessingException {
    /* given */
    final var batchRequest = mock(BatchRequest.class);
    final var userTaskRecord = (Record<UserTaskRecordValue>) mock(Record.class);
    final var variablesDueCompletion =
        Map.of("answer", 42, "duff", Map.of("price", 5, "amount", "5l"));
    final var userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(1L)
            .withTenantId(DEFAULT_TENANT_ID)
            .withAction("complete")
            .withVariables(variablesDueCompletion)
            .build();
    when(userTaskTemplate.getFullQualifiedName()).thenReturn("user-task-index");
    when(userTaskRecord.getIntent()).thenReturn(UserTaskIntent.COMPLETED);
    when(userTaskRecord.getValue()).thenReturn(userTaskRecordValue);
    /* when */
    userTaskZeebeRecordProcessor.processUserTaskRecord(batchRequest, userTaskRecord);
    /* then */
    final Map<String, Object> expectedUpdateFields =
        Map.of(
            "action",
            "complete",
            "variables",
            objectMapper.writeValueAsString(variablesDueCompletion));
    verifyUpsert(batchRequest, expectedUpdateFields);
  }

  @Test
  void canceledEvent() throws Exception {
    /* given */
    final var batchRequest = mock(BatchRequest.class);
    final var userTaskRecord = (Record<UserTaskRecordValue>) mock(Record.class);
    final var userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(1L)
            .withTenantId(DEFAULT_TENANT_ID)
            .withAction("")
            .build();
    when(userTaskTemplate.getFullQualifiedName()).thenReturn("user-task-index");
    when(userTaskRecord.getIntent()).thenReturn(UserTaskIntent.CANCELED);
    when(userTaskRecord.getValue()).thenReturn(userTaskRecordValue);
    /* when */
    userTaskZeebeRecordProcessor.processUserTaskRecord(batchRequest, userTaskRecord);
    /* then */
    final Map<String, Object> expectedUpdateFields = Map.of("action", "");
    verifyUpsert(batchRequest, expectedUpdateFields);
  }

  private void verifyUpsert(
      final BatchRequest batchRequest, final Map<String, Object> expectedUpdateFields)
      throws PersistenceException {
    verify(batchRequest)
        .upsert(
            eq("user-task-index"), eq("1"), any(UserTaskEntity.class), eq(expectedUpdateFields));
  }
}

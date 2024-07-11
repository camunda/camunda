/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.UserTaskEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.UserTaskTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UserTaskHandlerTest {

  private UserTaskHandler underTest;

  @Mock private UserTaskTemplate mockUserTaskTemplate;

  @Mock private ObjectMapper mockObjectMapper;

  @BeforeEach
  public void setup() {
    underTest = new UserTaskHandler(mockUserTaskTemplate, mockObjectMapper);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.USER_TASK);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(UserTaskEntity.class);
  }

  @Test
  public void testHandlesRecordWithAcceptIntent() {
    final Record<UserTaskRecordValue> mockUserTaskRecord = Mockito.mock(Record.class);
    when(mockUserTaskRecord.getIntent()).thenReturn(UserTaskIntent.CREATED);
    assertThat(underTest.handlesRecord(mockUserTaskRecord)).isTrue();
  }

  @Test
  public void testHandlesRecordWithUpdateIntent() {
    final Record<UserTaskRecordValue> mockUserTaskRecord = Mockito.mock(Record.class);
    when(mockUserTaskRecord.getIntent()).thenReturn(UserTaskIntent.ASSIGNED);
    assertThat(underTest.handlesRecord(mockUserTaskRecord)).isTrue();
  }

  @Test
  public void testHandlesRecordWithUnsupportedIntent() {
    final Record<UserTaskRecordValue> mockUserTaskRecord = Mockito.mock(Record.class);
    when(mockUserTaskRecord.getIntent()).thenReturn(UserTaskIntent.CANCELING);
    assertThat(underTest.handlesRecord(mockUserTaskRecord)).isFalse();
  }

  @Test
  public void testGenerateIds() {
    final Record<UserTaskRecordValue> mockUserTaskRecord = Mockito.mock(Record.class);
    final UserTaskRecordValue mockUserTaskRecordValue = Mockito.mock(UserTaskRecordValue.class);

    when(mockUserTaskRecord.getValue()).thenReturn(mockUserTaskRecordValue);
    when(mockUserTaskRecordValue.getUserTaskKey()).thenReturn(123L);

    final var idList = underTest.generateIds(mockUserTaskRecord);

    assertThat(idList).isNotNull();
    assertThat(idList).containsExactly("123");
  }

  @Test
  public void testCreateNewEntity() {
    final var result = (underTest.createNewEntity("id"));
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  public void testGetIndexName() {
    final String expectedIndexName = "operate-usertask";
    when(mockUserTaskTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    assertThat(underTest.getIndexName()).isEqualTo(expectedIndexName);
    verify(mockUserTaskTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testFlush() throws PersistenceException {
    final String expectedIndexName = "operate-usertask";
    when(mockUserTaskTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);

    final UserTaskEntity inputEntity = new UserTaskEntity();

    inputEntity.setId("id");
    inputEntity.setUserTaskKey(111L);
    inputEntity.setPartitionId(1);
    inputEntity.setBpmnProcessId("bpmnProcessId");
    inputEntity.setTenantId("tenant1");
    inputEntity.setProcessInstanceKey(222L);
    inputEntity.setAssignee("assignee");
    inputEntity.setCandidateGroups(List.of("g1", "g2"));
    inputEntity.setCandidateUsers(List.of("user1", "user2"));
    inputEntity.setDueDate(OffsetDateTime.now());
    inputEntity.setFollowUpDate(OffsetDateTime.now());
    inputEntity.setElementId("elementId");
    inputEntity.setElementInstanceKey(333L);
    inputEntity.setProcessDefinitionKey(444L);
    inputEntity.setProcessDefinitionVersion(1);
    inputEntity.setVariables("{ \"k1\": \"v1\" }");
    inputEntity.setFormKey(2L);
    inputEntity.setChangedAttributes(List.of("a1", "a2"));
    inputEntity.setAction("action");

    final Map<String, Object> expectedUpdateFields = new HashMap<>();
    expectedUpdateFields.put(UserTaskTemplate.KEY, inputEntity.getKey());
    expectedUpdateFields.put(UserTaskTemplate.USER_TASK_KEY, inputEntity.getUserTaskKey());
    expectedUpdateFields.put(UserTaskTemplate.PARTITION_ID, inputEntity.getPartitionId());
    expectedUpdateFields.put(UserTaskTemplate.BPMN_PROCESS_ID, inputEntity.getBpmnProcessId());
    expectedUpdateFields.put(UserTaskTemplate.TENANT_ID, inputEntity.getTenantId());
    expectedUpdateFields.put(
        UserTaskTemplate.PROCESS_INSTANCE_KEY, inputEntity.getProcessInstanceKey());
    expectedUpdateFields.put(UserTaskTemplate.ASSIGNEE, inputEntity.getAssignee());
    expectedUpdateFields.put(UserTaskTemplate.CANDIDATE_GROUPS, inputEntity.getCandidateGroups());
    expectedUpdateFields.put(UserTaskTemplate.CANDIDATE_USERS, inputEntity.getCandidateUsers());
    expectedUpdateFields.put(UserTaskTemplate.DUE_DATE, inputEntity.getDueDate());
    expectedUpdateFields.put(UserTaskTemplate.FOLLOW_UP_DATE, inputEntity.getFollowUpDate());
    expectedUpdateFields.put(UserTaskTemplate.ELEMENT_ID, inputEntity.getElementId());
    expectedUpdateFields.put(
        UserTaskTemplate.ELEMENT_INSTANCE_KEY, inputEntity.getElementInstanceKey());
    expectedUpdateFields.put(
        UserTaskTemplate.PROCESS_DEFINITION_KEY, inputEntity.getProcessDefinitionKey());
    expectedUpdateFields.put(
        UserTaskTemplate.PROCESS_DEFINITION_VERSION, inputEntity.getProcessDefinitionVersion());
    expectedUpdateFields.put(UserTaskTemplate.VARIABLES, inputEntity.getVariables());
    expectedUpdateFields.put(UserTaskTemplate.FORM_KEY, inputEntity.getFormKey());
    expectedUpdateFields.put(
        UserTaskTemplate.CHANGED_ATTRIBUTES, inputEntity.getChangedAttributes());
    expectedUpdateFields.put(UserTaskTemplate.ACTION, inputEntity.getAction());

    underTest.flush(inputEntity, mockRequest);

    verify(mockRequest, times(1))
        .upsert(expectedIndexName, inputEntity.getId(), inputEntity, expectedUpdateFields);
    verify(mockUserTaskTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testUpdateEntity() throws JsonProcessingException {
    final Record<UserTaskRecordValue> mockUserTaskRecord = Mockito.mock(Record.class);
    final UserTaskRecordValue mockUserTaskRecordValue = Mockito.mock(UserTaskRecordValue.class);

    when(mockUserTaskRecord.getValue()).thenReturn(mockUserTaskRecordValue);

    when(mockUserTaskRecordValue.getUserTaskKey()).thenReturn(111L);
    when(mockUserTaskRecord.getPartitionId()).thenReturn(1);
    when(mockUserTaskRecordValue.getBpmnProcessId()).thenReturn("bpmnId");
    when(mockUserTaskRecordValue.getTenantId()).thenReturn("tenant1");
    when(mockUserTaskRecordValue.getProcessInstanceKey()).thenReturn(222L);
    when(mockUserTaskRecordValue.getAssignee()).thenReturn("assignee");
    when(mockUserTaskRecordValue.getCandidateUsersList()).thenReturn(List.of("user1", "user2"));
    when(mockUserTaskRecordValue.getCandidateGroupsList()).thenReturn(List.of("g1", "g2"));
    when(mockUserTaskRecordValue.getElementId()).thenReturn("elementId");
    when(mockUserTaskRecordValue.getElementInstanceKey()).thenReturn(333L);
    when(mockUserTaskRecordValue.getProcessDefinitionKey()).thenReturn(444L);
    when(mockUserTaskRecordValue.getProcessDefinitionVersion()).thenReturn(2);
    when(mockUserTaskRecordValue.getVariables()).thenReturn(Map.of("k1", "v1"));
    when(mockUserTaskRecordValue.getFormKey()).thenReturn(2L);
    when(mockUserTaskRecordValue.getChangedAttributes()).thenReturn(List.of("a", "b"));
    when(mockUserTaskRecordValue.getAction()).thenReturn("action");

    when(mockObjectMapper.writeValueAsString(Map.of("k1", "v1"))).thenReturn("{\"k1\":\"v1\"}");

    final UserTaskEntity userTaskEntity = new UserTaskEntity();
    underTest.updateEntity(mockUserTaskRecord, userTaskEntity);

    assertThat(userTaskEntity.getId()).isEqualTo("111");
    assertThat(userTaskEntity.getKey()).isEqualTo(111L);
    assertThat(userTaskEntity.getUserTaskKey()).isEqualTo(111L);
    assertThat(userTaskEntity.getPartitionId()).isEqualTo(1);
    assertThat(userTaskEntity.getBpmnProcessId()).isEqualTo("bpmnId");
    assertThat(userTaskEntity.getTenantId()).isEqualTo("tenant1");
    assertThat(userTaskEntity.getProcessInstanceKey()).isEqualTo(222L);
    assertThat(userTaskEntity.getAssignee()).isEqualTo("assignee");
    assertThat(userTaskEntity.getCandidateGroups()).isEqualTo(List.of("g1", "g2"));
    assertThat(userTaskEntity.getCandidateUsers()).isEqualTo(List.of("user1", "user2"));
    assertThat(userTaskEntity.getElementId()).isEqualTo("elementId");
    assertThat(userTaskEntity.getElementInstanceKey()).isEqualTo(333L);
    assertThat(userTaskEntity.getProcessDefinitionKey()).isEqualTo(444L);
    assertThat(userTaskEntity.getProcessDefinitionVersion()).isEqualTo(2);
    assertThat(userTaskEntity.getVariables()).isEqualTo("{\"k1\":\"v1\"}");
    assertThat(userTaskEntity.getFormKey()).isEqualTo(2L);
    assertThat(userTaskEntity.getChangedAttributes()).isEqualTo(List.of("a", "b"));
    assertThat(userTaskEntity.getAction()).isEqualTo("action");

    verify(mockObjectMapper, times(1)).writeValueAsString(Map.of("k1", "v1"));
  }
}

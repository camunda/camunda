/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
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
  public void testHandlesRecord() {
    final Record<UserTaskRecordValue> mockUserTaskRecord = Mockito.mock(Record.class);
    when(mockUserTaskRecord.getIntent()).thenReturn(UserTaskIntent.CREATED);
    assertThat(underTest.handlesRecord(mockUserTaskRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<UserTaskRecordValue> mockUserTaskRecord = Mockito.mock(Record.class);
    final UserTaskRecordValue mockUserTaskRecordValue = Mockito.mock(UserTaskRecordValue.class);

    when(mockUserTaskRecord.getValue()).thenReturn(mockUserTaskRecordValue);
    when(mockUserTaskRecordValue.getUserTaskKey()).thenReturn(123L);

    final String expectedId = "123";

    final var idList = underTest.generateIds(mockUserTaskRecord);

    assertThat(idList).isNotNull();
    assertThat(idList).containsExactly(expectedId);
  }

  @Test
  public void testCreateNewEntity() {
    final var result = (underTest.createNewEntity("id"));
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  public void testFlush() throws PersistenceException {
    final String expectedIndexName = "operate-user-task";
    when(mockUserTaskTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    final UserTaskEntity inputEntity = new UserTaskEntity().setId("12");
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);

    underTest.flush(inputEntity, mockRequest);

    verify(mockRequest, times(1)).upsert(expectedIndexName, "12", inputEntity, new HashMap<>());
    verify(mockUserTaskTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testGetIndexName() {
    final String expectedIndexName = "operate-user-task";
    when(mockUserTaskTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    assertThat(underTest.getIndexName()).isEqualTo(expectedIndexName);
    verify(mockUserTaskTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testUpdateEntity() throws JsonProcessingException {

    final Record<UserTaskRecordValue> mockUserTaskRecord = Mockito.mock(Record.class);
    final UserTaskRecordValue mockUserTaskRecordValue = Mockito.mock(UserTaskRecordValue.class);

    when(mockUserTaskRecord.getValue()).thenReturn(mockUserTaskRecordValue);
    when(mockObjectMapper.writeValueAsString(anyMap())).thenReturn("vars");

    when(mockUserTaskRecordValue.getUserTaskKey()).thenReturn(123L);
    when(mockUserTaskRecord.getPartitionId()).thenReturn(5);
    when(mockUserTaskRecordValue.getBpmnProcessId()).thenReturn("bpmnProcessId");
    when(mockUserTaskRecordValue.getTenantId()).thenReturn("tenantId");
    when(mockUserTaskRecordValue.getProcessInstanceKey()).thenReturn(333L);
    when(mockUserTaskRecordValue.getAssignee()).thenReturn("assignee");
    when(mockUserTaskRecordValue.getCandidateGroupsList()).thenReturn(List.of("group1", "group2"));
    when(mockUserTaskRecordValue.getCandidateUsersList()).thenReturn(List.of("user1", "user2"));
    when(mockUserTaskRecordValue.getDueDate()).thenReturn(null);
    when(mockUserTaskRecordValue.getFollowUpDate()).thenReturn(null);
    when(mockUserTaskRecordValue.getElementId()).thenReturn("elementId");
    when(mockUserTaskRecordValue.getElementInstanceKey()).thenReturn(4567L);
    when(mockUserTaskRecordValue.getProcessDefinitionKey()).thenReturn(77L);
    when(mockUserTaskRecordValue.getProcessDefinitionVersion()).thenReturn(3);
    when(mockUserTaskRecordValue.getVariables()).thenReturn(Map.of());
    when(mockUserTaskRecordValue.getFormKey()).thenReturn(444L);
    when(mockUserTaskRecordValue.getChangedAttributes()).thenReturn(List.of("attr1", "attr2"));
    when(mockUserTaskRecordValue.getAction()).thenReturn("act");

    final UserTaskEntity userTaskEntity = new UserTaskEntity();
    underTest.updateEntity(mockUserTaskRecord, userTaskEntity);

    assertThat(userTaskEntity.getId()).isEqualTo("123");
    assertThat(userTaskEntity.getKey()).isEqualTo(123L);
    assertThat(userTaskEntity.getUserTaskKey()).isEqualTo(123L);
    assertThat(userTaskEntity.getPartitionId()).isEqualTo(5);
    assertThat(userTaskEntity.getBpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(userTaskEntity.getTenantId()).isEqualTo("tenantId");
    assertThat(userTaskEntity.getProcessInstanceKey()).isEqualTo(333L);
    assertThat(userTaskEntity.getAssignee()).isEqualTo("assignee");
    assertThat(userTaskEntity.getCandidateGroups()).containsExactlyInAnyOrder("group1", "group2");
    assertThat(userTaskEntity.getCandidateUsers()).containsExactlyInAnyOrder("user1", "user2");
    assertThat(userTaskEntity.getDueDate()).isNull();
    assertThat(userTaskEntity.getFollowUpDate()).isNull();
    assertThat(userTaskEntity.getElementId()).isEqualTo("elementId");
    assertThat(userTaskEntity.getElementInstanceKey()).isEqualTo(4567L);
    assertThat(userTaskEntity.getProcessDefinitionKey()).isEqualTo(77L);
    assertThat(userTaskEntity.getProcessDefinitionVersion()).isEqualTo(3);
    assertThat(userTaskEntity.getVariables()).isEqualTo("vars");
    assertThat(userTaskEntity.getFormKey()).isEqualTo(444L);
    assertThat(userTaskEntity.getChangedAttributes()).containsExactlyInAnyOrder("attr1", "attr2");
    assertThat(userTaskEntity.getAction()).isEqualTo("act");
  }
}

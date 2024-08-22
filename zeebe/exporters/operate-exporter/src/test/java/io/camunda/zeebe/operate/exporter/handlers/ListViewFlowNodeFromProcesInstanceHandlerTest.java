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

import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ListViewFlowNodeFromProcesInstanceHandlerTest {

  private ListViewFlowNodeFromProcesInstanceHandler underTest;

  @Mock private ListViewTemplate mockListViewTemplate;

  @BeforeEach
  public void setup() {
    underTest = new ListViewFlowNodeFromProcesInstanceHandler(mockListViewTemplate, false);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(FlowNodeInstanceForListViewEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Record<ProcessInstanceRecordValue> mockProcessInstanceRecord = Mockito.mock(Record.class);
    when(mockProcessInstanceRecord.getIntent())
        .thenReturn(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    assertThat(underTest.handlesRecord(mockProcessInstanceRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<ProcessInstanceRecordValue> mockProcessInstanceRecord = Mockito.mock(Record.class);
    when(mockProcessInstanceRecord.getKey()).thenReturn(123L);

    final String expectedId = "123";

    final var idList = underTest.generateIds(mockProcessInstanceRecord);

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
    final String expectedIndexName = "operate-list-view";
    when(mockListViewTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    final FlowNodeInstanceForListViewEntity inputEntity =
        new FlowNodeInstanceForListViewEntity()
            .setProcessInstanceKey(66L)
            .setPosition(123L)
            .setActivityId("A")
            .setActivityType(FlowNodeType.CALL_ACTIVITY)
            .setActivityState(FlowNodeState.ACTIVE);
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put("position", 123L);
    expectedUpdateFields.put("activityId", "A");
    expectedUpdateFields.put("activityType", FlowNodeType.CALL_ACTIVITY);
    expectedUpdateFields.put("activityState", FlowNodeState.ACTIVE);

    underTest.flush(inputEntity, mockRequest);

    verify(mockRequest, times(1))
        .upsertWithRouting(expectedIndexName, null, inputEntity, expectedUpdateFields, "66");
    verify(mockListViewTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testGetIndexName() {
    final String expectedIndexName = "operate-list-view";
    when(mockListViewTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    assertThat(underTest.getIndexName()).isEqualTo(expectedIndexName);
    verify(mockListViewTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testUpdateEntity() {

    final Record<ProcessInstanceRecordValue> mockProcessInstanceRecord = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue mockProcessInstanceRecordValue =
        Mockito.mock(ProcessInstanceRecordValue.class);

    when(mockProcessInstanceRecord.getValue()).thenReturn(mockProcessInstanceRecordValue);
    when(mockProcessInstanceRecord.getKey()).thenReturn(123L);
    when(mockProcessInstanceRecord.getPartitionId()).thenReturn(3);
    when(mockProcessInstanceRecord.getPosition()).thenReturn(55L);
    when(mockProcessInstanceRecord.getIntent())
        .thenReturn(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    when(mockProcessInstanceRecordValue.getElementId()).thenReturn("elementId");
    when(mockProcessInstanceRecordValue.getProcessInstanceKey()).thenReturn(222L);
    when(mockProcessInstanceRecordValue.getTenantId()).thenReturn("tenantId");

    final FlowNodeInstanceForListViewEntity flowNodeInstanceForListViewEntity =
        new FlowNodeInstanceForListViewEntity();
    underTest.updateEntity(mockProcessInstanceRecord, flowNodeInstanceForListViewEntity);

    assertThat(flowNodeInstanceForListViewEntity.getId()).isEqualTo("123");
    assertThat(flowNodeInstanceForListViewEntity.getKey()).isEqualTo(123L);
    assertThat(flowNodeInstanceForListViewEntity.getPartitionId()).isEqualTo(3);
    assertThat(flowNodeInstanceForListViewEntity.getPosition()).isEqualTo(55L);
    assertThat(flowNodeInstanceForListViewEntity.getActivityId()).isEqualTo("elementId");
    assertThat(flowNodeInstanceForListViewEntity.getProcessInstanceKey()).isEqualTo(222L);
    assertThat(flowNodeInstanceForListViewEntity.getTenantId()).isEqualTo("tenantId");
    assertThat(flowNodeInstanceForListViewEntity.getJoinRelation().getParent()).isEqualTo(222L);
    assertThat(flowNodeInstanceForListViewEntity.getActivityState())
        .isEqualTo(FlowNodeState.ACTIVE);
  }
}

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

import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.DateUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ListViewProcessInstanceFromProcessInstanceHandlerTest {

  private ListViewProcessInstanceFromProcessInstanceHandler underTest;

  @Mock private ListViewTemplate mockListViewTemplate;

  @BeforeEach
  public void setup() {
    underTest = new ListViewProcessInstanceFromProcessInstanceHandler(mockListViewTemplate, false);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(ProcessInstanceForListViewEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Record<ProcessInstanceRecordValue> mockProcessInstanceRecord = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue mockProcessInstanceRecordValue =
        Mockito.mock(ProcessInstanceRecordValue.class);

    when(mockProcessInstanceRecord.getValue()).thenReturn(mockProcessInstanceRecordValue);
    when(mockProcessInstanceRecordValue.getBpmnElementType()).thenReturn(BpmnElementType.PROCESS);
    when(mockProcessInstanceRecord.getIntent())
        .thenReturn(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    assertThat(underTest.handlesRecord(mockProcessInstanceRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<ProcessInstanceRecordValue> mockProcessInstanceRecord = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue mockProcessInstanceRecordValue =
        Mockito.mock(ProcessInstanceRecordValue.class);

    when(mockProcessInstanceRecord.getValue()).thenReturn(mockProcessInstanceRecordValue);
    when(mockProcessInstanceRecordValue.getProcessInstanceKey()).thenReturn(123L);

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

    final ProcessInstanceForListViewEntity inputEntity =
        new ProcessInstanceForListViewEntity()
            .setId("22")
            .setProcessName("process")
            .setProcessVersion(2)
            .setProcessDefinitionKey(444L)
            .setBpmnProcessId("bpmnProcessId")
            .setProcessInstanceKey(66L)
            .setPosition(123L);
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(ListViewTemplate.PROCESS_NAME, "process");
    expectedUpdateFields.put(ListViewTemplate.PROCESS_VERSION, 2);
    expectedUpdateFields.put(ListViewTemplate.PROCESS_KEY, 444L);
    expectedUpdateFields.put(ListViewTemplate.BPMN_PROCESS_ID, "bpmnProcessId");
    expectedUpdateFields.put("position", 123L);

    underTest.flush(inputEntity, mockRequest);

    verify(mockRequest, times(1))
        .upsert(expectedIndexName, "22", inputEntity, expectedUpdateFields);
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

    final long timestamp = new Date().getTime();
    when(mockProcessInstanceRecord.getValue()).thenReturn(mockProcessInstanceRecordValue);
    when(mockProcessInstanceRecord.getPartitionId()).thenReturn(3);
    when(mockProcessInstanceRecord.getPosition()).thenReturn(55L);
    when(mockProcessInstanceRecord.getTimestamp()).thenReturn(timestamp);
    when(mockProcessInstanceRecord.getIntent())
        .thenReturn(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    when(mockProcessInstanceRecord.getPosition()).thenReturn(55L);
    when(mockProcessInstanceRecordValue.getProcessInstanceKey()).thenReturn(66L);
    when(mockProcessInstanceRecordValue.getProcessDefinitionKey()).thenReturn(222L);
    when(mockProcessInstanceRecordValue.getBpmnProcessId()).thenReturn("bpmnProcessId");
    when(mockProcessInstanceRecordValue.getVersion()).thenReturn(7);
    when(mockProcessInstanceRecordValue.getTenantId()).thenReturn("tenantId");
    when(mockProcessInstanceRecordValue.getParentProcessInstanceKey()).thenReturn(777L);
    when(mockProcessInstanceRecordValue.getParentElementInstanceKey()).thenReturn(111L);

    final ProcessInstanceForListViewEntity processInstanceForListViewEntity =
        new ProcessInstanceForListViewEntity();
    underTest.updateEntity(mockProcessInstanceRecord, processInstanceForListViewEntity);

    assertThat(processInstanceForListViewEntity.getId()).isEqualTo("66");
    assertThat(processInstanceForListViewEntity.getProcessInstanceKey()).isEqualTo(66L);
    assertThat(processInstanceForListViewEntity.getKey()).isEqualTo(66L);
    assertThat(processInstanceForListViewEntity.getTenantId()).isEqualTo("tenantId");
    assertThat(processInstanceForListViewEntity.getPartitionId()).isEqualTo(3);
    assertThat(processInstanceForListViewEntity.getPosition()).isEqualTo(55L);
    assertThat(processInstanceForListViewEntity.getProcessDefinitionKey()).isEqualTo(222L);
    assertThat(processInstanceForListViewEntity.getBpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(processInstanceForListViewEntity.getProcessVersion()).isEqualTo(7);
    assertThat(processInstanceForListViewEntity.getProcessName()).isEqualTo("bpmnProcessId");
    assertThat(processInstanceForListViewEntity.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(processInstanceForListViewEntity.getStartDate())
        .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(timestamp)));
    assertThat(processInstanceForListViewEntity.getParentProcessInstanceKey()).isEqualTo(777L);
    assertThat(processInstanceForListViewEntity.getParentFlowNodeInstanceKey()).isEqualTo(111L);
    assertThat(processInstanceForListViewEntity.getTreePath()).isEqualTo("PI_66");
  }
}

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

import io.camunda.operate.entities.SequenceFlowEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SequenceFlowHandlerTest {
  private SequenceFlowHandler underTest;

  @Mock private SequenceFlowTemplate mockSequenceFlowIndex;

  @BeforeEach
  public void setup() {
    underTest = new SequenceFlowHandler(mockSequenceFlowIndex);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(SequenceFlowEntity.class);
  }

  @Test
  public void testHandlesValidRecord() {
    final Record<ProcessInstanceRecordValue> mockSequenceFlowRecord = Mockito.mock(Record.class);
    when(mockSequenceFlowRecord.getIntent()).thenReturn(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN);
    assertThat(underTest.handlesRecord(mockSequenceFlowRecord)).isTrue();
  }

  @Test
  public void testDoesNotHandleInvalidRecord() {
    final Record<ProcessInstanceRecordValue> mockSequenceFlowRecord = Mockito.mock(Record.class);
    when(mockSequenceFlowRecord.getIntent()).thenReturn(ProcessInstanceIntent.ACTIVATE_ELEMENT);
    assertThat(underTest.handlesRecord(mockSequenceFlowRecord)).isFalse();
  }

  @Test
  public void testGenerateIds() {
    final Record<ProcessInstanceRecordValue> mockSequenceFlowRecord = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue mockSequenceFlowRecordValue =
        Mockito.mock(ProcessInstanceRecordValue.class);

    when(mockSequenceFlowRecord.getValue()).thenReturn(mockSequenceFlowRecordValue);
    when(mockSequenceFlowRecordValue.getProcessInstanceKey()).thenReturn(123L);
    when(mockSequenceFlowRecordValue.getElementId()).thenReturn("element");

    final String expectedId = String.format("%s_%s", 123L, "element");

    final var idList = underTest.generateIds(mockSequenceFlowRecord);

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
    final String expectedIndexName = "operate-sequence";
    when(mockSequenceFlowIndex.getFullQualifiedName()).thenReturn(expectedIndexName);

    final SequenceFlowEntity inputEntity = new SequenceFlowEntity();
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);

    underTest.flush(inputEntity, mockRequest);

    verify(mockRequest, times(1)).add(expectedIndexName, inputEntity);
    verify(mockSequenceFlowIndex, times(1)).getFullQualifiedName();
  }

  @Test
  public void testGetIndexName() {
    final String expectedIndexName = "operate-sequence";
    when(mockSequenceFlowIndex.getFullQualifiedName()).thenReturn(expectedIndexName);

    assertThat(underTest.getIndexName()).isEqualTo(expectedIndexName);
    verify(mockSequenceFlowIndex, times(1)).getFullQualifiedName();
  }

  @Test
  public void testUpdateEntity() {
    final Record<ProcessInstanceRecordValue> mockSequenceFlowRecord = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue mockSequenceFlowRecordValue =
        Mockito.mock(ProcessInstanceRecordValue.class);

    when(mockSequenceFlowRecord.getValue()).thenReturn(mockSequenceFlowRecordValue);
    when(mockSequenceFlowRecordValue.getProcessInstanceKey()).thenReturn(111L);
    when(mockSequenceFlowRecordValue.getElementId()).thenReturn("element");
    when(mockSequenceFlowRecordValue.getProcessDefinitionKey()).thenReturn(222L);
    when(mockSequenceFlowRecordValue.getBpmnProcessId()).thenReturn("bpmnId");
    when(mockSequenceFlowRecordValue.getTenantId()).thenReturn("tenant1");

    final SequenceFlowEntity sequenceFlowEntity = new SequenceFlowEntity();
    underTest.updateEntity(mockSequenceFlowRecord, sequenceFlowEntity);

    assertThat(sequenceFlowEntity.getId()).isEqualTo(String.format("%s_%s", 111L, "element"));
    assertThat(sequenceFlowEntity.getProcessInstanceKey()).isEqualTo(111L);
    assertThat(sequenceFlowEntity.getProcessDefinitionKey()).isEqualTo(222L);
    assertThat(sequenceFlowEntity.getBpmnProcessId()).isEqualTo("bpmnId");
    assertThat(sequenceFlowEntity.getTenantId()).isEqualTo("tenant1");
  }
}

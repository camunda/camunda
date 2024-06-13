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

import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class VariableHandlerTest {

  private VariableHandler underTest;

  @Mock private VariableTemplate mockVariableIndex;

  private final int variableSizeThreshold = 5;

  @BeforeEach
  public void setup() {
    underTest = new VariableHandler(mockVariableIndex, variableSizeThreshold);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.VARIABLE);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(VariableEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Record<VariableRecordValue> mockVariableRecord = Mockito.mock(Record.class);
    assertThat(underTest.handlesRecord(mockVariableRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<VariableRecordValue> mockVariableRecord = Mockito.mock(Record.class);
    final VariableRecordValue mockVariableRecordValue = Mockito.mock(VariableRecordValue.class);

    when(mockVariableRecord.getValue()).thenReturn(mockVariableRecordValue);
    when(mockVariableRecordValue.getScopeKey()).thenReturn(123L);
    when(mockVariableRecordValue.getName()).thenReturn("name");

    final String expectedId = VariableForListViewEntity.getIdBy(123L, "name");

    final var idList = underTest.generateIds(mockVariableRecord);

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
    final String expectedIndexName = "operate-variable";
    when(mockVariableIndex.getFullQualifiedName()).thenReturn(expectedIndexName);

    final VariableEntity inputEntity = new VariableEntity();
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);

    underTest.flush(inputEntity, mockRequest);

    verify(mockRequest, times(1)).add(expectedIndexName, inputEntity);
    verify(mockVariableIndex, times(1)).getFullQualifiedName();
  }

  @Test
  public void testGetIndexName() {
    final String expectedIndexName = "operate-variable";
    when(mockVariableIndex.getFullQualifiedName()).thenReturn(expectedIndexName);

    assertThat(underTest.getIndexName()).isEqualTo(expectedIndexName);
    verify(mockVariableIndex, times(1)).getFullQualifiedName();
  }

  @Test
  public void testUpdateEntityWithPreviewVariable() {
    final Record<VariableRecordValue> mockVariableRecord = Mockito.mock(Record.class);
    final VariableRecordValue mockVariableRecordValue = Mockito.mock(VariableRecordValue.class);
    when(mockVariableRecord.getValue()).thenReturn(mockVariableRecordValue);

    when(mockVariableRecordValue.getScopeKey()).thenReturn(111L);
    when(mockVariableRecordValue.getName()).thenReturn("name");
    when(mockVariableRecord.getKey()).thenReturn(222L);
    when(mockVariableRecord.getPartitionId()).thenReturn(1);
    when(mockVariableRecordValue.getProcessInstanceKey()).thenReturn(333L);
    when(mockVariableRecordValue.getProcessDefinitionKey()).thenReturn(444L);
    when(mockVariableRecordValue.getBpmnProcessId()).thenReturn("bpmnId");
    when(mockVariableRecordValue.getTenantId()).thenReturn("tenant1");
    when(mockVariableRecord.getPosition()).thenReturn(10L);
    when(mockVariableRecordValue.getValue()).thenReturn("1234567890");

    final VariableEntity variableEntity = new VariableEntity();
    underTest.updateEntity(mockVariableRecord, variableEntity);

    assertThat(variableEntity.getId()).isEqualTo(VariableForListViewEntity.getIdBy(111L, "name"));
    assertThat(variableEntity.getKey()).isEqualTo(222L);
    assertThat(variableEntity.getPartitionId()).isEqualTo(1);
    assertThat(variableEntity.getScopeKey()).isEqualTo(111L);
    assertThat(variableEntity.getProcessInstanceKey()).isEqualTo(333L);
    assertThat(variableEntity.getProcessDefinitionKey()).isEqualTo(444L);
    assertThat(variableEntity.getBpmnProcessId()).isEqualTo("bpmnId");
    assertThat(variableEntity.getPosition()).isEqualTo(10L);
    assertThat(variableEntity.getTenantId()).isEqualTo("tenant1");
    assertThat(variableEntity.getValue()).isEqualTo(("12345"));
    assertThat(variableEntity.getFullValue()).isEqualTo("1234567890");
    assertThat(variableEntity.getIsPreview()).isTrue();
  }

  @Test
  public void testUpdateEntityWithNonPreviewVariable() {
    final Record<VariableRecordValue> mockVariableRecord = Mockito.mock(Record.class);
    final VariableRecordValue mockVariableRecordValue = Mockito.mock(VariableRecordValue.class);
    when(mockVariableRecord.getValue()).thenReturn(mockVariableRecordValue);

    when(mockVariableRecordValue.getScopeKey()).thenReturn(111L);
    when(mockVariableRecordValue.getName()).thenReturn("name");
    when(mockVariableRecord.getKey()).thenReturn(222L);
    when(mockVariableRecord.getPartitionId()).thenReturn(1);
    when(mockVariableRecordValue.getProcessInstanceKey()).thenReturn(333L);
    when(mockVariableRecordValue.getProcessDefinitionKey()).thenReturn(444L);
    when(mockVariableRecordValue.getBpmnProcessId()).thenReturn("bpmnId");
    when(mockVariableRecordValue.getTenantId()).thenReturn("tenant1");
    when(mockVariableRecord.getPosition()).thenReturn(10L);
    when(mockVariableRecordValue.getValue()).thenReturn("12345");

    final VariableEntity variableEntity = new VariableEntity();
    underTest.updateEntity(mockVariableRecord, variableEntity);

    assertThat(variableEntity.getId()).isEqualTo(VariableForListViewEntity.getIdBy(111L, "name"));
    assertThat(variableEntity.getKey()).isEqualTo(222L);
    assertThat(variableEntity.getPartitionId()).isEqualTo(1);
    assertThat(variableEntity.getScopeKey()).isEqualTo(111L);
    assertThat(variableEntity.getProcessInstanceKey()).isEqualTo(333L);
    assertThat(variableEntity.getProcessDefinitionKey()).isEqualTo(444L);
    assertThat(variableEntity.getBpmnProcessId()).isEqualTo("bpmnId");
    assertThat(variableEntity.getPosition()).isEqualTo(10L);
    assertThat(variableEntity.getTenantId()).isEqualTo("tenant1");
    assertThat(variableEntity.getValue()).isEqualTo(("12345"));
    assertThat(variableEntity.getFullValue()).isNull();
    assertThat(variableEntity.getIsPreview()).isFalse();
  }
}

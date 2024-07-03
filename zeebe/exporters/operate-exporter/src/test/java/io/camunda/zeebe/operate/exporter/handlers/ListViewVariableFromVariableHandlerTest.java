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

import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ListViewVariableFromVariableHandlerTest {

  private ListViewVariableFromVariableHandler underTest;

  @Mock private ListViewTemplate mockListViewTemplate;

  @BeforeEach
  public void setup() {
    underTest = new ListViewVariableFromVariableHandler(mockListViewTemplate, false);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.VARIABLE);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(VariableForListViewEntity.class);
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
    final String expectedIndexName = "operate-list-view";
    when(mockListViewTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    final VariableForListViewEntity inputEntity =
        new VariableForListViewEntity()
            .setProcessInstanceKey(66L)
            .setPosition(123L)
            .setVarName("A")
            .setVarValue("B");
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put("position", 123L);
    expectedUpdateFields.put("varName", "A");
    expectedUpdateFields.put("varValue", "B");

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
    final Record<VariableRecordValue> mockVariableRecord = Mockito.mock(Record.class);
    final VariableRecordValue mockVariableRecordValue = Mockito.mock(VariableRecordValue.class);
    when(mockVariableRecord.getValue()).thenReturn(mockVariableRecordValue);

    when(mockVariableRecordValue.getScopeKey()).thenReturn(111L);
    when(mockVariableRecordValue.getName()).thenReturn("name");
    when(mockVariableRecordValue.getValue()).thenReturn("1234567890");
    when(mockVariableRecord.getKey()).thenReturn(222L);
    when(mockVariableRecord.getPartitionId()).thenReturn(1);
    when(mockVariableRecordValue.getProcessInstanceKey()).thenReturn(333L);
    when(mockVariableRecordValue.getTenantId()).thenReturn("tenant1");
    when(mockVariableRecord.getPosition()).thenReturn(10L);

    final VariableForListViewEntity variableEntity = new VariableForListViewEntity();
    underTest.updateEntity(mockVariableRecord, variableEntity);

    assertThat(variableEntity.getId()).isEqualTo(VariableForListViewEntity.getIdBy(111L, "name"));
    assertThat(variableEntity.getKey()).isEqualTo(222L);
    assertThat(variableEntity.getPartitionId()).isEqualTo(1);
    assertThat(variableEntity.getScopeKey()).isEqualTo(111L);
    assertThat(variableEntity.getProcessInstanceKey()).isEqualTo(333L);
    assertThat(variableEntity.getPosition()).isEqualTo(10L);
    assertThat(variableEntity.getVarName()).isEqualTo("name");
    assertThat(variableEntity.getVarValue()).isEqualTo("1234567890");
    assertThat(variableEntity.getTenantId()).isEqualTo("tenant1");
  }
}

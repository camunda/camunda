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

import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ListViewFlowNodeFromIncidentHandlerTest {

  private ListViewFlowNodeFromIncidentHandler underTest;

  @Mock private ListViewTemplate mockListViewTemplate;

  @BeforeEach
  public void setup() {
    underTest = new ListViewFlowNodeFromIncidentHandler(mockListViewTemplate, false);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.INCIDENT);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(FlowNodeInstanceForListViewEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    assertThat(underTest.handlesRecord(mockIncidentRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    final IncidentRecordValue mockIncidentRecordValue = Mockito.mock(IncidentRecordValue.class);

    when(mockIncidentRecord.getValue()).thenReturn(mockIncidentRecordValue);
    when(mockIncidentRecordValue.getElementInstanceKey()).thenReturn(123L);

    final String expectedId = "123";

    final var idList = underTest.generateIds(mockIncidentRecord);

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
            .setErrorMessage("error")
            .setPositionIncident(123L);
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put("errorMessage", "error");
    expectedUpdateFields.put("positionIncident", 123L);

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

    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    final IncidentRecordValue mockIncidentRecordValue = Mockito.mock(IncidentRecordValue.class);

    when(mockIncidentRecord.getValue()).thenReturn(mockIncidentRecordValue);
    when(mockIncidentRecord.getPartitionId()).thenReturn(3);
    when(mockIncidentRecord.getPosition()).thenReturn(55L);
    when(mockIncidentRecord.getIntent()).thenReturn(IncidentIntent.CREATED);
    when(mockIncidentRecordValue.getElementInstanceKey()).thenReturn(123L);
    when(mockIncidentRecordValue.getElementId()).thenReturn("elementId");
    when(mockIncidentRecordValue.getErrorMessage()).thenReturn("errorMessage");
    when(mockIncidentRecordValue.getProcessInstanceKey()).thenReturn(222L);
    when(mockIncidentRecordValue.getTenantId()).thenReturn("tenantId");

    final FlowNodeInstanceForListViewEntity flowNodeInstanceForListViewEntity =
        new FlowNodeInstanceForListViewEntity();
    underTest.updateEntity(mockIncidentRecord, flowNodeInstanceForListViewEntity);

    assertThat(flowNodeInstanceForListViewEntity.getId()).isEqualTo("123");
    assertThat(flowNodeInstanceForListViewEntity.getKey()).isEqualTo(123L);
    assertThat(flowNodeInstanceForListViewEntity.getPartitionId()).isEqualTo(3);
    assertThat(flowNodeInstanceForListViewEntity.getActivityId()).isEqualTo("elementId");
    assertThat(flowNodeInstanceForListViewEntity.getProcessInstanceKey()).isEqualTo(222L);
    assertThat(flowNodeInstanceForListViewEntity.getErrorMessage()).isEqualTo("errorMessage");
    assertThat(flowNodeInstanceForListViewEntity.getTenantId()).isEqualTo("tenantId");
    assertThat(flowNodeInstanceForListViewEntity.getJoinRelation().getParent()).isEqualTo(222L);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static io.camunda.zeebe.protocol.record.value.ErrorType.IO_MAPPING_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.IncidentTemplate;
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
public class IncidentHandlerTest {

  private IncidentHandler underTest;

  @Mock private IncidentTemplate mockIncidentTemplate;

  @BeforeEach
  public void setup() {
    underTest = new IncidentHandler(mockIncidentTemplate, false);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.INCIDENT);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(IncidentEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    when(mockIncidentRecord.getIntent()).thenReturn(IncidentIntent.CREATED);
    assertThat(underTest.handlesRecord(mockIncidentRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    when(mockIncidentRecord.getKey()).thenReturn(123L);

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
    final String expectedIndexName = "incident";
    when(mockIncidentTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    final IncidentEntity inputEntity = new IncidentEntity().setKey(333L).setPosition(555L);
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);
    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put("position", 555L);

    underTest.flush(inputEntity, mockRequest);

    verify(mockRequest, times(1))
        .upsert(expectedIndexName, "333", inputEntity, expectedUpdateFields);
    verify(mockIncidentTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testGetIndexName() {
    final String expectedIndexName = "incident";
    when(mockIncidentTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    assertThat(underTest.getIndexName()).isEqualTo(expectedIndexName);
    verify(mockIncidentTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testUpdateEntity() {

    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    final IncidentRecordValue mockIncidentRecordValue = Mockito.mock(IncidentRecordValue.class);

    when(mockIncidentRecord.getValue()).thenReturn(mockIncidentRecordValue);
    when(mockIncidentRecord.getKey()).thenReturn(25L);
    when(mockIncidentRecord.getPartitionId()).thenReturn(10);
    when(mockIncidentRecord.getPosition()).thenReturn(65L);
    when(mockIncidentRecordValue.getProcessInstanceKey()).thenReturn(123L);
    when(mockIncidentRecordValue.getProcessDefinitionKey()).thenReturn(444L);
    when(mockIncidentRecordValue.getElementInstanceKey()).thenReturn(66L);
    when(mockIncidentRecordValue.getElementId()).thenReturn("elementId");
    when(mockIncidentRecordValue.getErrorMessage()).thenReturn("error");
    when(mockIncidentRecordValue.getErrorType()).thenReturn(IO_MAPPING_ERROR);
    when(mockIncidentRecordValue.getJobKey()).thenReturn(222L);
    when(mockIncidentRecordValue.getBpmnProcessId()).thenReturn("bpmnProcessId");
    when(mockIncidentRecordValue.getErrorMessage()).thenReturn("error");
    when(mockIncidentRecordValue.getTenantId()).thenReturn("tenantId");

    final IncidentEntity incidentEntity = new IncidentEntity();
    underTest.updateEntity(mockIncidentRecord, incidentEntity);

    assertThat(incidentEntity.getId()).isEqualTo("25");
    assertThat(incidentEntity.getKey()).isEqualTo(25L);
    assertThat(incidentEntity.getPartitionId()).isEqualTo(10);
    assertThat(incidentEntity.getPosition()).isEqualTo(65L);
    assertThat(incidentEntity.getProcessInstanceKey()).isEqualTo(123L);
    assertThat(incidentEntity.getProcessDefinitionKey()).isEqualTo(444L);
    assertThat(incidentEntity.getBpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(incidentEntity.getJobKey()).isEqualTo(222L);
    assertThat(incidentEntity.getFlowNodeId()).isEqualTo("elementId");
    assertThat(incidentEntity.getFlowNodeInstanceKey()).isEqualTo(66L);
    assertThat(incidentEntity.getErrorMessage()).isEqualTo("error");
    assertThat(incidentEntity.getErrorType()).isEqualTo(ErrorType.IO_MAPPING_ERROR);
    assertThat(incidentEntity.getTenantId()).isEqualTo("tenantId");
  }
}

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

import io.camunda.operate.entities.MetricEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.MetricIndex;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.DateUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MetricFromProcessInstanceHandlerTest {

  private MetricFromProcessInstanceHandler underTest;

  @Mock private MetricIndex mockMetricIndex;

  @BeforeEach
  public void setup() {
    underTest = new MetricFromProcessInstanceHandler(mockMetricIndex);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(MetricEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Record<ProcessInstanceRecordValue> mockProcessInstanceRecord = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue mockProcessInstanceRecordValue =
        Mockito.mock(ProcessInstanceRecordValue.class);
    when(mockProcessInstanceRecord.getValue()).thenReturn(mockProcessInstanceRecordValue);
    when(mockProcessInstanceRecordValue.getParentProcessInstanceKey()).thenReturn(-1L);
    when(mockProcessInstanceRecord.getIntent())
        .thenReturn(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    assertThat(underTest.handlesRecord(mockProcessInstanceRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<ProcessInstanceRecordValue> mockProcessInstanceRecord = Mockito.mock(Record.class);
    final var idList = underTest.generateIds(mockProcessInstanceRecord);

    assertThat(idList).isNotNull();
    assertThat(idList).isEmpty();
  }

  @Test
  public void testCreateNewEntity() {
    final var result = (underTest.createNewEntity("id"));
    assertThat(result).isNotNull();
    assertThat(result.getId()).isNull();
  }

  @Test
  public void testFlush() throws PersistenceException {
    final String expectedIndexName = "operate-metric";
    when(mockMetricIndex.getFullQualifiedName()).thenReturn(expectedIndexName);

    final MetricEntity inputEntity = new MetricEntity();
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);

    underTest.flush(inputEntity, mockRequest);

    verify(mockRequest, times(1)).add(expectedIndexName, inputEntity);
    verify(mockMetricIndex, times(1)).getFullQualifiedName();
  }

  @Test
  public void testGetIndexName() {
    final String expectedIndexName = "operate-metric";
    when(mockMetricIndex.getFullQualifiedName()).thenReturn(expectedIndexName);

    assertThat(underTest.getIndexName()).isEqualTo(expectedIndexName);
    verify(mockMetricIndex, times(1)).getFullQualifiedName();
  }

  @Test
  public void testUpdateEntity() {

    final Record<ProcessInstanceRecordValue> mockProcessInstanceRecord = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue mockProcessInstanceRecordValue =
        Mockito.mock(ProcessInstanceRecordValue.class);

    final long timestamp = new Date().getTime();
    when(mockProcessInstanceRecord.getValue()).thenReturn(mockProcessInstanceRecordValue);
    when(mockProcessInstanceRecord.getTimestamp()).thenReturn(timestamp);
    when(mockProcessInstanceRecordValue.getProcessInstanceKey()).thenReturn(123L);
    when(mockProcessInstanceRecordValue.getTenantId()).thenReturn("tenantId");

    final MetricEntity metricEntity = new MetricEntity();
    underTest.updateEntity(mockProcessInstanceRecord, metricEntity);

    assertThat(metricEntity.getId()).isNull();
    assertThat(metricEntity.getEvent()).isEqualTo(MetricsStore.EVENT_PROCESS_INSTANCE_STARTED);
    assertThat(metricEntity.getValue()).isEqualTo("123");
    assertThat(metricEntity.getEventTime())
        .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(timestamp)));
    assertThat(metricEntity.getTenantId()).isEqualTo("tenantId");
  }
}

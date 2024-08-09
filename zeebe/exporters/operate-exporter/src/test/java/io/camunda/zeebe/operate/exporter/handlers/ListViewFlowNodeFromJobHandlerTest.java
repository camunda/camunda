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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ListViewFlowNodeFromJobHandlerTest {

  private ListViewFlowNodeFromJobHandler underTest;

  @Mock private ListViewTemplate mockListViewTemplate;

  @BeforeEach
  public void setup() {
    underTest = new ListViewFlowNodeFromJobHandler(mockListViewTemplate, false);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.JOB);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(FlowNodeInstanceForListViewEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Record<JobRecordValue> mockJobRecord = Mockito.mock(Record.class);
    assertThat(underTest.handlesRecord(mockJobRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<JobRecordValue> mockJobRecord = Mockito.mock(Record.class);
    final JobRecordValue mockJobRecordValue = Mockito.mock(JobRecordValue.class);

    when(mockJobRecord.getValue()).thenReturn(mockJobRecordValue);
    when(mockJobRecordValue.getElementInstanceKey()).thenReturn(123L);

    final String expectedId = "123";

    final var idList = underTest.generateIds(mockJobRecord);

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
        new FlowNodeInstanceForListViewEntity().setProcessInstanceKey(66L).setPositionJob(123L);
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put("jobFailedWithRetriesLeft", false);
    expectedUpdateFields.put("positionJob", 123L);

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

    final Record<JobRecordValue> mockJobRecord = Mockito.mock(Record.class);
    final JobRecordValue mockJobRecordValue = Mockito.mock(JobRecordValue.class);

    when(mockJobRecord.getValue()).thenReturn(mockJobRecordValue);
    when(mockJobRecord.getPartitionId()).thenReturn(3);
    when(mockJobRecord.getPosition()).thenReturn(55L);
    when(mockJobRecord.getIntent()).thenReturn(JobIntent.FAIL);
    when(mockJobRecordValue.getElementInstanceKey()).thenReturn(123L);
    when(mockJobRecordValue.getElementId()).thenReturn("elementId");
    when(mockJobRecordValue.getProcessInstanceKey()).thenReturn(222L);
    when(mockJobRecordValue.getTenantId()).thenReturn("tenantId");

    final FlowNodeInstanceForListViewEntity flowNodeInstanceForListViewEntity =
        new FlowNodeInstanceForListViewEntity();
    underTest.updateEntity(mockJobRecord, flowNodeInstanceForListViewEntity);

    assertThat(flowNodeInstanceForListViewEntity.getId()).isEqualTo("123");
    assertThat(flowNodeInstanceForListViewEntity.getKey()).isEqualTo(123L);
    assertThat(flowNodeInstanceForListViewEntity.getPartitionId()).isEqualTo(3);
    assertThat(flowNodeInstanceForListViewEntity.getPositionJob()).isEqualTo(55L);
    assertThat(flowNodeInstanceForListViewEntity.getActivityId()).isEqualTo("elementId");
    assertThat(flowNodeInstanceForListViewEntity.getProcessInstanceKey()).isEqualTo(222L);
    assertThat(flowNodeInstanceForListViewEntity.getTenantId()).isEqualTo("tenantId");
    assertThat(flowNodeInstanceForListViewEntity.getJoinRelation().getParent()).isEqualTo(222L);
    assertThat(flowNodeInstanceForListViewEntity.isJobFailedWithRetriesLeft()).isEqualTo(false);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.JOB_POSITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ListViewJoinRelation;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ListViewFlowNodeFromJobHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-list-view";
  private final ListViewFlowNodeFromJobHandler underTest =
      new ListViewFlowNodeFromJobHandler(indexName);

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.JOB);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(FlowNodeInstanceForListViewEntity.class);
  }

  @Test
  public void shouldHandlesRecord() {
    // given
    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            r ->
                r.withValueType(ValueType.JOB)
                    .withValue(
                        ImmutableJobRecordValue.builder()
                            .withProcessDefinitionKey(1L)
                            .withElementInstanceKey(111L)
                            .withProcessInstanceKey(222L)
                            .build())
                    .withKey(111L));
    // when - then
    assertThat(underTest.handlesRecord(jobRecord)).isTrue();
  }

  @Test
  public void shouldNotHandlesRecord() {
    // given
    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            r ->
                r.withValueType(ValueType.JOB)
                    .withValue(
                        ImmutableJobRecordValue.builder()
                            .withProcessDefinitionKey(1L)
                            .withElementInstanceKey(111L)
                            .withProcessInstanceKey(111L)
                            .build())
                    .withKey(111L));
    // when - then
    assertThat(underTest.handlesRecord(jobRecord)).isFalse();
  }

  @Test
  public void shouldGenerateIds() {
    // given
    final Record<JobRecordValue> jobRecord = factory.generateRecord(ValueType.JOB);

    // when
    final var idList = underTest.generateIds(jobRecord);

    // then
    assertThat(idList)
        .containsExactly(String.valueOf(jobRecord.getValue().getElementInstanceKey()));
  }

  @Test
  public void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");
    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  public void shouldUpsertEntityOnFlush() {
    // given
    final FlowNodeInstanceForListViewEntity inputEntity =
        new FlowNodeInstanceForListViewEntity()
            .setId("111")
            .setKey(111L)
            .setPartitionId(3)
            .setPositionJob(123L)
            .setActivityId("A")
            .setProcessInstanceKey(66L)
            .setTenantId("tenantId")
            .setJobFailedWithRetriesLeft(true);
    inputEntity.getJoinRelation().setParent(66L);

    final BatchRequest mockRequest = mock(BatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(JOB_POSITION, 123L);
    expectedUpdateFields.put(ListViewTemplate.JOB_FAILED_WITH_RETRIES_LEFT, true);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName,
            inputEntity.getId(),
            inputEntity,
            expectedUpdateFields,
            String.valueOf(inputEntity.getProcessInstanceKey()));
  }

  @Test
  public void shouldUpdateEntityFromRecord() {
    // given
    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(ValueType.JOB, r -> r.withIntent(JobIntent.CREATED));

    // when
    final FlowNodeInstanceForListViewEntity flowNodeInstanceForListViewEntity =
        new FlowNodeInstanceForListViewEntity();
    underTest.updateEntity(jobRecord, flowNodeInstanceForListViewEntity);

    // then
    assertThat(flowNodeInstanceForListViewEntity.getId())
        .isEqualTo(String.valueOf(jobRecord.getValue().getElementInstanceKey()));
    assertThat(flowNodeInstanceForListViewEntity.getKey())
        .isEqualTo(jobRecord.getValue().getElementInstanceKey());
    assertThat(flowNodeInstanceForListViewEntity.getPartitionId())
        .isEqualTo(jobRecord.getPartitionId());
    assertThat(flowNodeInstanceForListViewEntity.getPositionJob())
        .isEqualTo(jobRecord.getPosition());
    assertThat(flowNodeInstanceForListViewEntity.getPosition()).isNull();
    assertThat(flowNodeInstanceForListViewEntity.getActivityId())
        .isEqualTo(jobRecord.getValue().getElementId());
    assertThat(flowNodeInstanceForListViewEntity.getProcessInstanceKey())
        .isEqualTo(jobRecord.getValue().getProcessInstanceKey());
    assertThat(flowNodeInstanceForListViewEntity.getTenantId())
        .isEqualTo(jobRecord.getValue().getTenantId());
    assertThat(flowNodeInstanceForListViewEntity.isJobFailedWithRetriesLeft()).isEqualTo(false);
    assertThat(flowNodeInstanceForListViewEntity.getJoinRelation())
        .isEqualTo(
            new ListViewJoinRelation(ListViewTemplate.ACTIVITIES_JOIN_RELATION)
                .setParent(jobRecord.getValue().getProcessInstanceKey()));
  }

  @Test
  public void shouldSetJobFailWithRetry() {
    // given
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withRetries(2)
            .build();
    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(ValueType.JOB, r -> r.withIntent(JobIntent.FAILED).withValue(value));

    // when
    final FlowNodeInstanceForListViewEntity flowNodeInstanceForListViewEntity =
        new FlowNodeInstanceForListViewEntity();
    underTest.updateEntity(jobRecord, flowNodeInstanceForListViewEntity);

    // then
    assertThat(flowNodeInstanceForListViewEntity.isJobFailedWithRetriesLeft()).isEqualTo(true);
  }

  @Test
  public void shouldNotSetJobFailWithRetry() {
    // given
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withRetries(0)
            .build();
    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(ValueType.JOB, r -> r.withIntent(JobIntent.FAILED).withValue(value));

    // when
    final FlowNodeInstanceForListViewEntity flowNodeInstanceForListViewEntity =
        new FlowNodeInstanceForListViewEntity();
    underTest.updateEntity(jobRecord, flowNodeInstanceForListViewEntity);

    // then
    assertThat(flowNodeInstanceForListViewEntity.isJobFailedWithRetriesLeft()).isEqualTo(false);
  }
}

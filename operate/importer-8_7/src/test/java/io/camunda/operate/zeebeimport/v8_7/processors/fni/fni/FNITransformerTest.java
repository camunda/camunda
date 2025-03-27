/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.v8_7.processors.fni.fni;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.camunda.operate.zeebeimport.cache.FNITreePathCacheCompositeKey;
import io.camunda.operate.zeebeimport.cache.TreePathCache;
import io.camunda.operate.zeebeimport.v8_7.processors.processors.fni.FNITransformer;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class FNITransformerTest {

  private FNITransformer fniTransformer;
  private TreePathCache mockTreePathCache;

  @BeforeEach
  public void setup() {
    mockTreePathCache = Mockito.mock(TreePathCache.class);
    when(mockTreePathCache.resolveParentTreePath(any()))
        .thenAnswer(
            invocationOnMock -> {
              final FNITreePathCacheCompositeKey compositeKey = invocationOnMock.getArgument(0);
              return String.format(
                  "%d/%d", compositeKey.processInstanceKey(), compositeKey.flowScopeKey());
            });
    fniTransformer = new FNITransformer(mockTreePathCache);
  }

  @Test
  public void shouldTransformActivateFNIRecord() {
    // given
    final var time = System.currentTimeMillis();
    final var record = createStartingZeebeRecord(time);

    // when
    final FlowNodeInstanceEntity flowNodeInstanceEntity =
        fniTransformer.toFlowNodeInstanceEntity(record, null);

    // then
    assertThat(flowNodeInstanceEntity).isNotNull();
    assertGeneralValues(flowNodeInstanceEntity);
    assertThat(flowNodeInstanceEntity.getTreePath()).isEqualTo("1/3/4");
    assertThat(flowNodeInstanceEntity.getLevel()).isEqualTo(2);
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(flowNodeInstanceEntity.getEndDate()).isNull();
    assertThat(flowNodeInstanceEntity.getStartDate().toInstant())
        .isEqualTo(Instant.ofEpochMilli(time));
  }

  @Test
  public void shouldNotCacheLeafFNI() {
    // given
    final var time = System.currentTimeMillis();
    final var record = createStartingZeebeRecord(time);

    // when
    fniTransformer.toFlowNodeInstanceEntity(record, null);

    // then
    Mockito.verify(mockTreePathCache, times(0)).cacheTreePath(any(), any());
  }

  @Test
  public void shouldCacheContainerFNI() {
    // given
    final var time = System.currentTimeMillis();
    final var record = createStartingZeebeRecord(time);

    // when
    fniTransformer.toFlowNodeInstanceEntity(record, null);

    // then
    Mockito.verify(mockTreePathCache, times(0)).cacheTreePath(any(), any());
  }

  @Test
  public void shouldTransformMigratedFNIRecord() {
    // given
    final var time = System.currentTimeMillis();
    final var record = createMigratedZeebeRecord(time);

    // when
    final FlowNodeInstanceEntity flowNodeInstanceEntity =
        fniTransformer.toFlowNodeInstanceEntity(record, null);

    // then
    assertThat(flowNodeInstanceEntity).isNotNull();
    assertGeneralValues(flowNodeInstanceEntity);
    assertThat(flowNodeInstanceEntity.getTreePath()).isEqualTo("1/3/4");
    assertThat(flowNodeInstanceEntity.getLevel()).isEqualTo(2);
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(flowNodeInstanceEntity.getEndDate()).isNull();
    assertThat(flowNodeInstanceEntity.getStartDate()).isNull();
  }

  @Test
  public void shouldTransformCompletedFNIRecord() {
    // given
    final var time = System.currentTimeMillis();
    final var record = createCompletedZeebeRecord(time);

    // when
    final FlowNodeInstanceEntity flowNodeInstanceEntity =
        fniTransformer.toFlowNodeInstanceEntity(record, null);

    // then
    assertThat(flowNodeInstanceEntity).isNotNull();
    assertGeneralValues(flowNodeInstanceEntity);
    assertThat(flowNodeInstanceEntity.getTreePath()).isNull();
    assertThat(flowNodeInstanceEntity.getLevel()).isZero();
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(flowNodeInstanceEntity.getStartDate()).isNull();
    assertThat(flowNodeInstanceEntity.getEndDate().toInstant())
        .isEqualTo(Instant.ofEpochMilli(time));
  }

  @Test
  public void shouldTransformMultipleRecordsIntoEntity() {
    // given
    final var time = System.currentTimeMillis();
    final var activated = createStartingZeebeRecord(time);
    var flowNodeInstanceEntity = fniTransformer.toFlowNodeInstanceEntity(activated, null);
    final var completed = createCompletedZeebeRecord(time);

    // when
    flowNodeInstanceEntity =
        fniTransformer.toFlowNodeInstanceEntity(completed, flowNodeInstanceEntity);

    // then
    assertThat(flowNodeInstanceEntity).isNotNull();
    assertGeneralValues(flowNodeInstanceEntity);
    assertThat(flowNodeInstanceEntity.getTreePath()).isEqualTo("1/3/4");
    assertThat(flowNodeInstanceEntity.getLevel()).isEqualTo(2);
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(flowNodeInstanceEntity.getStartDate().toInstant())
        .isEqualTo(Instant.ofEpochMilli(time));
    assertThat(flowNodeInstanceEntity.getEndDate().toInstant())
        .isEqualTo(Instant.ofEpochMilli(time));
  }

  @Test
  public void shouldTransformTerminatedFNIRecord() {
    // given
    final var time = System.currentTimeMillis();
    final var record = createTerminatedZeebeRecord(time);

    // when
    final FlowNodeInstanceEntity flowNodeInstanceEntity =
        fniTransformer.toFlowNodeInstanceEntity(record, null);

    // then
    assertThat(flowNodeInstanceEntity).isNotNull();
    assertGeneralValues(flowNodeInstanceEntity);
    assertThat(flowNodeInstanceEntity.getTreePath()).isNull();
    assertThat(flowNodeInstanceEntity.getLevel()).isZero();
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.TERMINATED);
    assertThat(flowNodeInstanceEntity.getStartDate()).isNull();
    assertThat(flowNodeInstanceEntity.getEndDate().toInstant())
        .isEqualTo(Instant.ofEpochMilli(time));
  }

  private static void assertGeneralValues(final FlowNodeInstanceEntity entity) {
    assertThat(entity.getBpmnProcessId()).isEqualTo("process");
    assertThat(entity.getFlowNodeId()).isEqualTo("element");
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(123);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(1);
    assertThat(entity.getKey()).isEqualTo(4L);
    assertThat(entity.getTenantId()).isEqualTo("none");
    assertThat(entity.getType()).isEqualTo(FlowNodeType.START_EVENT);
  }

  private static io.camunda.zeebe.protocol.record.Record createStartingZeebeRecord(
      final long timestamp) {
    return createZeebeRecord(timestamp, ProcessInstanceIntent.ELEMENT_ACTIVATING);
  }

  private static io.camunda.zeebe.protocol.record.Record createMigratedZeebeRecord(
      final long timestamp) {
    return createZeebeRecord(timestamp, ProcessInstanceIntent.ELEMENT_MIGRATED);
  }

  private static io.camunda.zeebe.protocol.record.Record createCompletedZeebeRecord(
      final long timestamp) {
    return createZeebeRecord(timestamp, ProcessInstanceIntent.ELEMENT_COMPLETED);
  }

  private static io.camunda.zeebe.protocol.record.Record createTerminatedZeebeRecord(
      final long timestamp) {
    return createZeebeRecord(timestamp, ProcessInstanceIntent.ELEMENT_TERMINATED);
  }

  protected static io.camunda.zeebe.protocol.record.Record createZeebeRecord(
      final long timestamp, final ProcessInstanceIntent intent) {
    return createZeebeRecord(timestamp, intent, BpmnElementType.START_EVENT);
  }

  protected static io.camunda.zeebe.protocol.record.Record createZeebeRecord(
      final long timestamp,
      final ProcessInstanceIntent intent,
      final BpmnElementType bpmnElementType) {
    final var recordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnProcessId("process")
            .withElementId("element")
            .withTenantId("none")
            .withProcessDefinitionKey(123)
            .withBpmnElementType(bpmnElementType)
            .withFlowScopeKey(3)
            .withProcessInstanceKey(1)
            .withVersion(12)
            .withBpmnEventType(BpmnEventType.NONE)
            .build();
    final var record = Mockito.mock(io.camunda.zeebe.protocol.record.Record.class);
    when(record.getKey()).thenReturn(4L);
    when(record.getValue()).thenReturn(recordValue);
    when(record.getIntent()).thenReturn(intent);
    when(record.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getTimestamp()).thenReturn(timestamp);
    return record;
  }
}

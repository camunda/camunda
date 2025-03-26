/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor.POSITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ListViewJoinRelation;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class ListViewFlowNodeFromProcessInstanceHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-list-view";
  private final ListViewFlowNodeFromProcessInstanceHandler underTest =
      new ListViewFlowNodeFromProcessInstanceHandler(indexName);

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(FlowNodeInstanceForListViewEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Set<ProcessInstanceIntent> intents2Handle =
        Set.of(
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            ProcessInstanceIntent.ELEMENT_TERMINATED,
            ProcessInstanceIntent.ELEMENT_MIGRATED);

    intents2Handle.stream()
        .forEach(
            intent -> {
              final Record<ProcessInstanceRecordValue> processInstanceRecord = createRecord(intent);
              // when - then
              assertThat(underTest.handlesRecord(processInstanceRecord))
                  .as("Handles intent %s", intent)
                  .isTrue();
            });
  }

  private Record<ProcessInstanceRecordValue> createRecord(final ProcessInstanceIntent intent) {
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.SERVICE_TASK)
            .build();
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r -> r.withIntent(intent).withValue(processInstanceRecordValue));
    return processInstanceRecord;
  }

  @Test
  public void shouldNotHandleRecord() {
    final Set<ProcessInstanceIntent> intents2Handle =
        Set.of(
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            ProcessInstanceIntent.ELEMENT_TERMINATED,
            ProcessInstanceIntent.ELEMENT_MIGRATED);
    final Set<ProcessInstanceIntent> intents2Ignore =
        Arrays.stream(ProcessInstanceIntent.values())
            .filter(intent -> !intents2Handle.contains(intent))
            .collect(Collectors.toSet());

    intents2Ignore.stream()
        .forEach(
            intent -> {
              final Record<ProcessInstanceRecordValue> processInstanceRecord = createRecord(intent);
              // when - then
              assertThat(underTest.handlesRecord(processInstanceRecord))
                  .as("Does not handle intent %s", intent)
                  .isFalse();
            });
  }

  @Test
  public void shouldNotHandleProcessRecord() {
    // given
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.PROCESS)
            .build();
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                    .withValue(processInstanceRecordValue));
    // when - then
    assertThat(underTest.handlesRecord(processInstanceRecord)).isFalse();
  }

  @Test
  public void shouldNotHandleSequenceFlowRecord() {
    // given
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.SEQUENCE_FLOW)
            .build();
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.ELEMENT_MIGRATED)
                    .withValue(processInstanceRecordValue));
    // when - then
    assertThat(underTest.handlesRecord(processInstanceRecord)).isFalse();
  }

  @Test
  public void shouldGenerateIds() {
    // given
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.SERVICE_TASK)
            .build();

    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                    .withValue(processInstanceRecordValue));

    // when
    final var idList = underTest.generateIds(processInstanceRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(processInstanceRecord.getKey()));
  }

  @Test
  public void shouldCreateNewEntity() {
    // when
    final var result = (underTest.createNewEntity("id"));

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldUpsertEntityOnFlush() {
    // given
    final FlowNodeInstanceForListViewEntity inputEntity =
        new FlowNodeInstanceForListViewEntity()
            .setId("111")
            .setPosition(123L)
            .setProcessInstanceKey(66L)
            .setPartitionId(3)
            .setActivityId("A")
            .setActivityType(FlowNodeType.CALL_ACTIVITY)
            .setActivityState(FlowNodeState.ACTIVE);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(POSITION, 123L);
    expectedUpdateFields.put(ListViewTemplate.ACTIVITY_ID, "A");
    expectedUpdateFields.put(ListViewTemplate.ACTIVITY_TYPE, FlowNodeType.CALL_ACTIVITY);
    expectedUpdateFields.put(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.ACTIVE);

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
  void shouldUpdateEntityFromRecord() {
    // given
    final long timestamp = new Date().getTime();
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.SERVICE_TASK)
            .build();

    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                    .withTimestamp(timestamp)
                    .withPartitionId(3)
                    .withPosition(55L)
                    .withValue(processInstanceRecordValue));

    // when
    final FlowNodeInstanceForListViewEntity flowNodeInstanceForListViewEntity =
        new FlowNodeInstanceForListViewEntity();
    underTest.updateEntity(processInstanceRecord, flowNodeInstanceForListViewEntity);

    // then
    assertThat(flowNodeInstanceForListViewEntity.getId())
        .isEqualTo(String.valueOf(processInstanceRecord.getKey()));
    assertThat(flowNodeInstanceForListViewEntity.getKey())
        .isEqualTo(processInstanceRecord.getKey());
    assertThat(flowNodeInstanceForListViewEntity.getProcessInstanceKey())
        .isEqualTo(processInstanceRecordValue.getProcessInstanceKey());
    assertThat(flowNodeInstanceForListViewEntity.getTenantId())
        .isEqualTo(processInstanceRecordValue.getTenantId());
    assertThat(flowNodeInstanceForListViewEntity.getPartitionId())
        .isEqualTo(processInstanceRecord.getPartitionId());
    assertThat(flowNodeInstanceForListViewEntity.getPosition())
        .isEqualTo(processInstanceRecord.getPosition());
    assertThat(flowNodeInstanceForListViewEntity.getActivityId())
        .isEqualTo(processInstanceRecordValue.getElementId());
    assertThat(flowNodeInstanceForListViewEntity.getActivityState())
        .isEqualTo(FlowNodeState.ACTIVE);
    assertThat(flowNodeInstanceForListViewEntity.getJoinRelation())
        .isEqualTo(
            new ListViewJoinRelation(ListViewTemplate.ACTIVITIES_JOIN_RELATION)
                .setParent(processInstanceRecordValue.getProcessInstanceKey()));
  }

  @Test
  void shouldUpdateStateForCompletedRecord() {
    // given
    final long timestamp = new Date().getTime();
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.SERVICE_TASK)
            .build();
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                    .withTimestamp(timestamp)
                    .withValue(processInstanceRecordValue));

    // when then
    final FlowNodeInstanceForListViewEntity entity = new FlowNodeInstanceForListViewEntity();
    underTest.updateEntity(processInstanceRecord, entity);

    assertThat(entity.getActivityState()).isEqualTo(FlowNodeState.COMPLETED);
  }

  @Test
  void shouldUpdateStateForTerminatedRecord() {
    // given
    final long timestamp = new Date().getTime();
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.SERVICE_TASK)
            .build();
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                    .withTimestamp(timestamp)
                    .withValue(processInstanceRecordValue));

    // when then
    final FlowNodeInstanceForListViewEntity entity = new FlowNodeInstanceForListViewEntity();
    underTest.updateEntity(processInstanceRecord, entity);

    assertThat(entity.getActivityState()).isEqualTo(FlowNodeState.TERMINATED);
  }
}

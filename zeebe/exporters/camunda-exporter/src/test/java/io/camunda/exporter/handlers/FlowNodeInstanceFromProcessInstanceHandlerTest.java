/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.operate.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.operate.FlowNodeState;
import io.camunda.webapps.schema.entities.operate.FlowNodeType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class FlowNodeInstanceFromProcessInstanceHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-list-view";
  private final FlowNodeInstanceFromProcessInstanceHandler underTest =
      new FlowNodeInstanceFromProcessInstanceHandler(indexName);

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(FlowNodeInstanceEntity.class);
  }

  @Test
  public void shouldHandleRecord() {
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
  public void shouldNotHandleNotProcessRecord() {
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
  public void shouldGenerateIds() {
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        createRecord(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    final var idList = underTest.generateIds(processInstanceRecord);

    assertThat(idList).isNotNull();
    assertThat(idList).containsExactly(String.valueOf(processInstanceRecord.getKey()));
  }

  @Test
  public void testCreateNewEntity() {
    final var result = underTest.createNewEntity("123");
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("123");
  }

  @Test
  public void shouldUpsertEntityOnFlush() {
    final FlowNodeInstanceEntity inputEntity =
        new FlowNodeInstanceEntity()
            .setId("111")
            .setKey(111)
            .setPartitionId(1)
            .setType(FlowNodeType.SERVICE_TASK)
            .setState(FlowNodeState.ACTIVE)
            .setTreePath(null)
            .setFlowNodeId("flowNode1")
            .setProcessDefinitionKey(222L)
            .setBpmnProcessId("bpmnId")
            .setTenantId("tenantId")
            .setLevel(0)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setPosition(333L);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new HashMap<>();
    expectedUpdateFields.put(FlowNodeInstanceTemplate.ID, inputEntity.getId());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.PARTITION_ID, inputEntity.getPartitionId());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.TYPE, inputEntity.getType());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.STATE, inputEntity.getState());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.TREE_PATH, inputEntity.getTreePath());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.FLOW_NODE_ID, inputEntity.getFlowNodeId());
    expectedUpdateFields.put(
        FlowNodeInstanceTemplate.PROCESS_DEFINITION_KEY, inputEntity.getProcessDefinitionKey());
    expectedUpdateFields.put(
        FlowNodeInstanceTemplate.BPMN_PROCESS_ID, inputEntity.getBpmnProcessId());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.LEVEL, inputEntity.getLevel());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.START_DATE, inputEntity.getStartDate());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.END_DATE, inputEntity.getEndDate());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.POSITION, inputEntity.getPosition());

    // when
    underTest.flush(inputEntity, mockRequest);
    // then
    verify(mockRequest, times(1))
        .upsert(indexName, inputEntity.getId(), inputEntity, expectedUpdateFields);
  }

  @Test
  public void shouldUpdateEntityFromRecord() {
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
                    .withValue(processInstanceRecordValue));

    // when
    final FlowNodeInstanceEntity flowNodeInstanceEntity = new FlowNodeInstanceEntity();
    underTest.updateEntity(processInstanceRecord, flowNodeInstanceEntity);

    // then
    assertThat(flowNodeInstanceEntity.getKey()).isEqualTo(processInstanceRecord.getKey());
    assertThat(flowNodeInstanceEntity.getId())
        .isEqualTo(String.valueOf(processInstanceRecord.getKey()));
    assertThat(flowNodeInstanceEntity.getPartitionId())
        .isEqualTo(processInstanceRecord.getPartitionId());
    assertThat(flowNodeInstanceEntity.getFlowNodeId())
        .isEqualTo(processInstanceRecordValue.getElementId());
    assertThat(flowNodeInstanceEntity.getProcessInstanceKey())
        .isEqualTo(processInstanceRecordValue.getProcessInstanceKey());
    assertThat(flowNodeInstanceEntity.getProcessDefinitionKey())
        .isEqualTo(processInstanceRecordValue.getProcessDefinitionKey());
    assertThat(flowNodeInstanceEntity.getBpmnProcessId())
        .isEqualTo(processInstanceRecordValue.getBpmnProcessId());
    assertThat(flowNodeInstanceEntity.getTenantId())
        .isEqualTo(processInstanceRecordValue.getTenantId());
    assertThat(flowNodeInstanceEntity.getTreePath())
        .isEqualTo(
            String.format(
                "%s/%s",
                processInstanceRecordValue.getProcessInstanceKey(),
                flowNodeInstanceEntity.getId()));
    assertThat(flowNodeInstanceEntity.getLevel()).isEqualTo(1);
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(flowNodeInstanceEntity.getEndDate()).isNull();
    assertThat(flowNodeInstanceEntity.getStartDate())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
    assertThat(flowNodeInstanceEntity.getPosition()).isEqualTo(processInstanceRecord.getPosition());
  }

  @Test
  public void shouldUpdateCompletedEntityFromRecord() {
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
                    .withValue(processInstanceRecordValue)
                    .withTimestamp(timestamp));

    // when
    final FlowNodeInstanceEntity flowNodeInstanceEntity = new FlowNodeInstanceEntity();
    underTest.updateEntity(processInstanceRecord, flowNodeInstanceEntity);

    // then
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(flowNodeInstanceEntity.getStartDate()).isNull();
    assertThat(flowNodeInstanceEntity.getEndDate())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
    assertThat(flowNodeInstanceEntity.getPosition()).isNull();
  }

  @Test
  public void shouldUpdateCancelledEntityFromRecord() {
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

    // when
    final FlowNodeInstanceEntity flowNodeInstanceEntity = new FlowNodeInstanceEntity();
    underTest.updateEntity(processInstanceRecord, flowNodeInstanceEntity);

    // then
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.TERMINATED);
    assertThat(flowNodeInstanceEntity.getStartDate()).isNull();
    assertThat(flowNodeInstanceEntity.getEndDate())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
    assertThat(flowNodeInstanceEntity.getPosition()).isNull();
  }

  @Test
  public void shouldUpdateMigratedEntityFromRecord() {
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
                r.withIntent(ProcessInstanceIntent.ELEMENT_MIGRATED)
                    .withValue(processInstanceRecordValue));

    // when
    final FlowNodeInstanceEntity flowNodeInstanceEntity = new FlowNodeInstanceEntity();
    underTest.updateEntity(processInstanceRecord, flowNodeInstanceEntity);

    // then
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(flowNodeInstanceEntity.getStartDate()).isNull();
    assertThat(flowNodeInstanceEntity.getEndDate()).isNull();
    assertThat(flowNodeInstanceEntity.getPosition()).isNull();
  }
}

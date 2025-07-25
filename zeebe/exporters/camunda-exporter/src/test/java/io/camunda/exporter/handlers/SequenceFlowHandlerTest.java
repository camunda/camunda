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
import io.camunda.webapps.schema.entities.SequenceFlowEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class SequenceFlowHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "sequence-flow";
  private final SequenceFlowHandler underTest = new SequenceFlowHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(SequenceFlowEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r -> r.withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN));

    // when - then
    assertThat(underTest.handlesRecord(processInstanceRecord)).isTrue();
  }

  @Test
  void shouldNotHandleRecord() {
    // given
    Arrays.stream(ProcessInstanceIntent.values())
        .filter(intent -> intent != ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
        .forEach(
            intent -> {
              final Record<ProcessInstanceRecordValue> processInstanceRecord =
                  factory.generateRecord(ValueType.PROCESS_INSTANCE, r -> r.withIntent(intent));

              // when - then
              assertThat(underTest.handlesRecord(processInstanceRecord)).isFalse();
            });
  }

  @Test
  void shouldGenerateIds() {
    // given
    final ProcessInstanceRecordValue sequenceFlowRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .build();

    final Record<ProcessInstanceRecordValue> sequenceFlowRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
                    .withValue(sequenceFlowRecordValue));

    // when
    final var idList = underTest.generateIds(sequenceFlowRecord);
    // then
    assertThat(idList)
        .containsExactly(
            sequenceFlowRecordValue.getProcessInstanceKey()
                + "_"
                + sequenceFlowRecordValue.getElementId());
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final SequenceFlowEntity inputEntity = new SequenceFlowEntity();
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .build();

    final Record<ProcessInstanceRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
                    .withValue(processInstanceRecordValue));

    // when
    final SequenceFlowEntity sequenceFlowEntity = new SequenceFlowEntity();
    underTest.updateEntity(variableRecord, sequenceFlowEntity);

    // then
    assertThat(sequenceFlowEntity.getId())
        .isEqualTo(
            processInstanceRecordValue.getProcessInstanceKey()
                + "_"
                + processInstanceRecordValue.getElementId());
    assertThat(sequenceFlowEntity.getProcessInstanceKey())
        .isEqualTo(processInstanceRecordValue.getProcessInstanceKey());
    assertThat(sequenceFlowEntity.getProcessDefinitionKey())
        .isEqualTo(processInstanceRecordValue.getProcessDefinitionKey());
    assertThat(sequenceFlowEntity.getBpmnProcessId())
        .isEqualTo(processInstanceRecordValue.getBpmnProcessId());
    assertThat(sequenceFlowEntity.getActivityId())
        .isEqualTo(processInstanceRecordValue.getElementId());
    assertThat(sequenceFlowEntity.getTenantId())
        .isEqualTo(processInstanceRecordValue.getTenantId());
  }
}

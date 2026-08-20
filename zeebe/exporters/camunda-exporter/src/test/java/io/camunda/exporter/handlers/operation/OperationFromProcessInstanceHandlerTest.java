/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.operation;

import static io.camunda.exporter.handlers.operation.OperationFromProcessInstanceHandler.ELIGIBLE_STATES;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperationFromProcessInstanceHandlerTest
    extends AbstractOperationHandlerTest<ProcessInstanceRecordValue> {

  @BeforeEach
  void setUp() {
    underTest = new OperationFromProcessInstanceHandler(indexName);
    valueType = ValueType.PROCESS_INSTANCE;
  }

  @Test
  void shouldHandleRecord() {
    final var value =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnElementType(BpmnElementType.PROCESS)
            .build();

    ELIGIBLE_STATES.stream()
        .map(i -> generateRecord(i, value))
        .forEach(record -> assertThat(underTest.handlesRecord(record)).isTrue());
  }

  @Test
  void shouldNotHandleRecordOfInvalidIntent() {
    final var value =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnElementType(BpmnElementType.PROCESS)
            .build();
    Stream.of(ProcessInstanceIntent.values())
        .filter(intent -> !ELIGIBLE_STATES.contains(intent))
        .map(i -> generateRecord(i, value))
        .forEach(record -> assertThat(underTest.handlesRecord(record)).isFalse());
  }

  @Test
  void shouldNotHandleRecordOfInvalidBpmnType() {
    final var value =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnElementType(BpmnElementType.SUB_PROCESS)
            .build();

    ELIGIBLE_STATES.stream()
        .map(i -> generateRecord(i, value))
        .forEach(record -> assertThat(underTest.handlesRecord(record)).isFalse());
  }
}

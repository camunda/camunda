/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation.listview;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import org.junit.jupiter.api.Test;

class ListViewFromProcessInstanceCancellationOperationHandlerTest
    extends AbstractProcessInstanceFromOperationItemHandlerTest<ProcessInstanceRecordValue> {

  ListViewFromProcessInstanceCancellationOperationHandlerTest() {
    super(new ListViewFromProcessInstanceCancellationOperationHandler(INDEX_NAME, CACHE));
  }

  @Test
  void shouldOnlyHandleRootProcessInstances() {
    final var value =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.PROCESS)
            .withParentProcessInstanceKey(12345) // This is a subprocess
            .build();
    final Record<ProcessInstanceRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            b -> b.withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED).withValue(value));

    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Override
  protected Record<ProcessInstanceRecordValue> createCompletedRecord(
      final long processInstanceKey) {
    final var value =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.PROCESS)
            .withProcessInstanceKey(processInstanceKey)
            .withParentProcessInstanceKey(-1)
            .build();
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        b -> b.withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED).withValue(value));
  }

  @Override
  protected Record<ProcessInstanceRecordValue> createRejectedRecord() {
    final var value =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.PROCESS)
            .withProcessInstanceKey(123456789L)
            .withParentProcessInstanceKey(-1)
            .build();
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        b ->
            b.withRecordType(RecordType.COMMAND_REJECTION)
                .withRejectionType(RejectionType.INVALID_STATE)
                .withIntent(ProcessInstanceIntent.CANCEL)
                .withValue(value));
  }

  @Override
  protected Record<ProcessInstanceRecordValue> createNotFoundRejectedRecord() {
    final var value =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.PROCESS)
            .withParentProcessInstanceKey(-1)
            .build();
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        b ->
            b.withRecordType(RecordType.COMMAND_REJECTION)
                .withRejectionType(RejectionType.NOT_FOUND)
                .withIntent(ProcessInstanceIntent.CANCEL)
                .withValue(value));
  }
}

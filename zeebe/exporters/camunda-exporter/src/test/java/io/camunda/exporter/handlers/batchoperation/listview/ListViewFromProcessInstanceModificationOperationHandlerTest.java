/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation.listview;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;

class ListViewFromProcessInstanceModificationOperationHandlerTest
    extends AbstractProcessInstanceFromOperationItemHandlerTest<
        ProcessInstanceModificationRecordValue> {

  ListViewFromProcessInstanceModificationOperationHandlerTest() {
    super(new ListViewFromProcessInstanceModificationOperationHandler(INDEX_NAME, CACHE));
  }

  @Override
  protected Record<ProcessInstanceModificationRecordValue> createCompletedRecord(
      final long processInstanceKey) {
    final var value =
        ImmutableProcessInstanceModificationRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceModificationRecordValue.class))
            .withProcessInstanceKey(processInstanceKey)
            .build();
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE_MODIFICATION,
        b -> b.withIntent(ProcessInstanceModificationIntent.MODIFIED).withValue(value));
  }

  @Override
  protected Record<ProcessInstanceModificationRecordValue> createRejectedRecord() {
    final var value =
        ImmutableProcessInstanceModificationRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceModificationRecordValue.class))
            .withProcessInstanceKey(123456789L)
            .build();
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE_MODIFICATION,
        b ->
            b.withRecordType(RecordType.COMMAND_REJECTION)
                .withRejectionType(RejectionType.INVALID_STATE)
                .withIntent(ProcessInstanceModificationIntent.MODIFY)
                .withValue(value));
  }

  @Override
  protected Record<ProcessInstanceModificationRecordValue> createNotFoundRejectedRecord() {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE_MODIFICATION,
        b ->
            b.withRecordType(RecordType.COMMAND_REJECTION)
                .withRejectionType(RejectionType.NOT_FOUND)
                .withIntent(ProcessInstanceModificationIntent.MODIFY));
  }
}

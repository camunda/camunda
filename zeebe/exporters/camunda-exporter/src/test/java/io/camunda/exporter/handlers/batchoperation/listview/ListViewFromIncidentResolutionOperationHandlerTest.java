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
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;

class ListViewFromIncidentResolutionOperationHandlerTest
    extends AbstractProcessInstanceFromOperationItemHandlerTest<IncidentRecordValue> {

  ListViewFromIncidentResolutionOperationHandlerTest() {
    super(new ListViewFromIncidentResolutionOperationHandler(INDEX_NAME, CACHE));
  }

  @Override
  protected io.camunda.zeebe.protocol.record.Record<IncidentRecordValue> createCompletedRecord(
      final long processInstanceKey) {
    final var value =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .withProcessInstanceKey(processInstanceKey)
            .build();
    return factory.generateRecord(
        ValueType.INCIDENT, b -> b.withIntent(IncidentIntent.RESOLVED).withValue(value));
  }

  @Override
  protected Record<IncidentRecordValue> createRejectedRecord() {
    final var value =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .withProcessInstanceKey(123456789L)
            .build();
    return factory.generateRecord(
        ValueType.INCIDENT,
        b ->
            b.withRecordType(RecordType.COMMAND_REJECTION)
                .withRejectionType(RejectionType.INVALID_STATE)
                .withIntent(IncidentIntent.RESOLVE)
                .withValue(value));
  }

  @Override
  protected Record<IncidentRecordValue> createNotFoundRejectedRecord() {
    return factory.generateRecord(
        ValueType.INCIDENT,
        b ->
            b.withRecordType(RecordType.COMMAND_REJECTION)
                .withRejectionType(RejectionType.NOT_FOUND)
                .withIntent(IncidentIntent.RESOLVE));
  }
}

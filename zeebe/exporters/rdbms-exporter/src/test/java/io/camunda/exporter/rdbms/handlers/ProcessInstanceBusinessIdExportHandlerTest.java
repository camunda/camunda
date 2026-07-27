/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.service.ProcessInstanceWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBusinessIdIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBusinessIdRecordValue;
import org.junit.jupiter.api.Test;

class ProcessInstanceBusinessIdExportHandlerTest {

  private static final long PROCESS_INSTANCE_KEY = 42L;
  private static final String BUSINESS_ID = "my-business-id";

  private final ProcessInstanceWriter processInstanceWriter = mock(ProcessInstanceWriter.class);
  private final ProcessInstanceBusinessIdExportHandler handler =
      new ProcessInstanceBusinessIdExportHandler(processInstanceWriter);

  @Test
  void shouldExportAssignedRecord() {
    // given
    final var record =
        record(ValueType.PROCESS_INSTANCE_BUSINESS_ID, ProcessInstanceBusinessIdIntent.ASSIGNED);

    // when - then
    assertThat(handler.canExport(record)).isTrue();
  }

  @Test
  void shouldNotExportAssignCommand() {
    // given
    final var record =
        record(ValueType.PROCESS_INSTANCE_BUSINESS_ID, ProcessInstanceBusinessIdIntent.ASSIGN);

    // when - then
    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldNotExportOtherValueType() {
    // given
    final var record = record(ValueType.PROCESS_INSTANCE, ProcessInstanceBusinessIdIntent.ASSIGNED);

    // when - then
    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldUpdateBusinessIdOnExport() {
    // given
    final var record =
        record(ValueType.PROCESS_INSTANCE_BUSINESS_ID, ProcessInstanceBusinessIdIntent.ASSIGNED);

    // when
    handler.export(record);

    // then
    verify(processInstanceWriter).updateBusinessId(PROCESS_INSTANCE_KEY, BUSINESS_ID);
  }

  @Test
  void shouldNormalizeEmptyBusinessIdToNullOnExport() {
    // given
    final var record =
        record(ValueType.PROCESS_INSTANCE_BUSINESS_ID, ProcessInstanceBusinessIdIntent.ASSIGNED);
    when(record.getValue().getBusinessId()).thenReturn("");

    // when
    handler.export(record);

    // then
    verify(processInstanceWriter).updateBusinessId(PROCESS_INSTANCE_KEY, null);
  }

  @SuppressWarnings("unchecked")
  private Record<ProcessInstanceBusinessIdRecordValue> record(
      final ValueType valueType, final ProcessInstanceBusinessIdIntent intent) {
    final Record<ProcessInstanceBusinessIdRecordValue> record = mock(Record.class);
    when(record.getValueType()).thenReturn(valueType);
    when(record.getIntent()).thenReturn(intent);
    final ProcessInstanceBusinessIdRecordValue value =
        mock(ProcessInstanceBusinessIdRecordValue.class);
    when(value.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(value.getBusinessId()).thenReturn(BUSINESS_ID);
    when(record.getValue()).thenReturn(value);
    return record;
  }
}

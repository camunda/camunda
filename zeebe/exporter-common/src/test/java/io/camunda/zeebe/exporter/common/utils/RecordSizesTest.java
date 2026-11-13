/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.WrittenRecord;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class RecordSizesTest {

  @Test
  void shouldUseTheLengthInTheLog() {
    // given
    final Record<?> record =
        mock(Record.class, withSettings().extraInterfaces(WrittenRecord.class));
    when(((WrittenRecord) record).getRawLength()).thenReturn(1234);

    // when
    final var size = RecordSizes.of(record);

    // then
    assertThat(size).isEqualTo(1234);
  }

  @Test
  void shouldUseTheSerializedLength() {
    // given
    final Record<?> record = mock(Record.class, withSettings().extraInterfaces(BufferWriter.class));
    when(((BufferWriter) record).getLength()).thenReturn(567);

    // when
    final var size = RecordSizes.of(record);

    // then
    assertThat(size).isEqualTo(567);
  }

  @Test
  void shouldUseTheSerializedLengthOfACopiedRecordsValue() {
    // given
    final var value =
        new VariableRecord()
            .setName(BufferUtil.wrapString("large"))
            .setValue(
                new UnsafeBuffer(
                    MsgPackConverter.convertToMsgPack("\"" + "x".repeat(100_000) + "\"")))
            .setScopeKey(1)
            .setProcessInstanceKey(1)
            .setProcessDefinitionKey(1)
            .setBpmnProcessId(BufferUtil.wrapString("process"));
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.VARIABLE)
            .intent(VariableIntent.CREATED);
    final var record = new CopiedRecord<>(value, metadata, 1, 1, 1, -1, 1);

    // when
    final var size = RecordSizes.of(record);

    // then
    assertThat(size).isEqualTo(value.getLength()).isGreaterThan(100_000);
  }

  @Test
  void shouldFallBackToTheLengthOfTheStringForm() {
    // given
    final var record = new ProtocolFactory().generateRecord();

    // when
    final var size = RecordSizes.of(record);

    // then
    assertThat(size).isEqualTo(record.toString().length()).isPositive();
  }
}

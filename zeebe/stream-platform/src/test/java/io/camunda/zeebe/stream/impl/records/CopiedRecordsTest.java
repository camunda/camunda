/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.util.Records;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class CopiedRecordsTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldCopyMetadataAndValueFromTheirRespectiveBuffers(final boolean sharedBuffer) {
    // given
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .intent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .valueType(ValueType.PROCESS_INSTANCE);
    final var value = Records.processInstance(123, "process-id");
    final int metadataOffset = 5;
    final int valueOffset = metadataOffset + metadata.getLength() + 7;
    final var metadataBuffer = new UnsafeBuffer(new byte[valueOffset + value.getLength()]);
    final var valueBuffer =
        sharedBuffer ? metadataBuffer : new UnsafeBuffer(new byte[valueOffset + value.getLength()]);
    metadata.write(metadataBuffer, metadataOffset);
    value.write(valueBuffer, valueOffset);

    final var event = mock(LoggedEvent.class);
    when(event.getMetadata()).thenReturn(metadataBuffer);
    when(event.getMetadataOffset()).thenReturn(metadataOffset);
    when(event.getMetadataLength()).thenReturn(metadata.getLength());
    when(event.getValueBuffer()).thenReturn(valueBuffer);
    when(event.getValueOffset()).thenReturn(valueOffset);
    when(event.getValueLength()).thenReturn(value.getLength());
    when(event.getKey()).thenReturn(10L);
    when(event.getPosition()).thenReturn(20L);
    when(event.getSourceEventPosition()).thenReturn(15L);
    when(event.getTimestamp()).thenReturn(30L);

    // when
    final var copied = CopiedRecords.createCopiedRecord(2, event);
    metadataBuffer.setMemory(0, metadataBuffer.capacity(), (byte) 0);
    valueBuffer.setMemory(0, valueBuffer.capacity(), (byte) 0);

    // then
    assertThat(copied.getValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
    assertThat(copied.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(copied.getIntent()).isEqualTo(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    assertThat(copied.getPartitionId()).isEqualTo(2);
    assertThat(copied.getKey()).isEqualTo(10L);
    assertThat(copied.getPosition()).isEqualTo(20L);
    assertThat(copied.getSourceRecordPosition()).isEqualTo(15L);
    assertThat(copied.getTimestamp()).isEqualTo(30L);
    assertThat(copied.getValue())
        .isInstanceOfSatisfying(
            ProcessInstanceRecord.class,
            copiedValue -> {
              assertThat(copiedValue.getProcessInstanceKey()).isEqualTo(123);
              assertThat(copiedValue.getBpmnProcessId()).isEqualTo("process-id");
            });
  }
}

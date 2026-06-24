/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.stream.Stream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class RecordMetadataBlockTest {

  private final RecordMetadataBlock block = new RecordMetadataBlock();

  @ParameterizedTest
  @MethodSource("testCases")
  void shouldDecodeAllFieldsConsistentlyWithRecordMetadata(
      final RecordType recordType,
      final ValueType valueType,
      final Intent intent,
      final int offset) {
    // given
    final var buffer = encodeMetadata(recordType, valueType, intent, offset);

    // when
    block.wrap(buffer, offset);

    final var fullMetadata = new RecordMetadata();
    fullMetadata.wrap(buffer, offset, buffer.capacity() - offset);

    // then
    assertThat(block.recordType()).isEqualTo(fullMetadata.getRecordType());
    assertThat(block.valueType()).isEqualTo(fullMetadata.getValueType());
    assertThat(block.intent()).isEqualTo(fullMetadata.getIntent());
  }

  @ParameterizedTest
  @MethodSource("testCases")
  void shouldBeReusableAcrossMultipleWraps(
      final RecordType recordType,
      final ValueType valueType,
      final Intent intent,
      final int offset) {
    // given — first wrap with different values
    final var otherBuffer =
        encodeMetadata(RecordType.COMMAND, ValueType.ERROR, ProcessInstanceIntent.CANCEL, 0);
    block.wrap(otherBuffer, 0);

    // when — second wrap overwrites state
    final var buffer = encodeMetadata(recordType, valueType, intent, offset);
    block.wrap(buffer, offset);

    // then
    assertThat(block.recordType()).isEqualTo(recordType);
    assertThat(block.valueType()).isEqualTo(valueType);
    assertThat(block.intent()).isEqualTo(intent);
  }

  private static UnsafeBuffer encodeMetadata(
      final RecordType recordType,
      final ValueType valueType,
      final Intent intent,
      final int offset) {
    final var metadata = new RecordMetadata();
    metadata.recordType(recordType).valueType(valueType).intent(intent);
    final var buffer = new UnsafeBuffer(new byte[offset + metadata.getLength()]);
    metadata.write(buffer, offset);
    return buffer;
  }

  static Stream<Arguments> testCases() {
    return Stream.of(
        Arguments.of(RecordType.COMMAND, ValueType.JOB, JobIntent.COMPLETE, 0),
        Arguments.of(
            RecordType.EVENT,
            ValueType.PROCESS_INSTANCE,
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            8),
        Arguments.of(RecordType.COMMAND_REJECTION, ValueType.JOB, JobIntent.TIME_OUT, 64));
  }
}

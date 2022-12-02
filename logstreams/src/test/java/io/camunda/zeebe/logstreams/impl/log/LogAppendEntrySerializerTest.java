/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static io.camunda.zeebe.dispatcher.impl.log.DataFrameDescriptor.framedLength;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.camunda.zeebe.logstreams.util.MutableLogAppendEntry;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

final class LogAppendEntrySerializerTest {
  private final MutableDirectBuffer writeBuffer = new ExpandableArrayBuffer();
  private final int writeBufferOffset = DataFrameDescriptor.messageOffset(0);

  @Test
  void shouldSerializeEntry() {
    // given
    final var serializer = new LogAppendEntrySerializer();
    final var event = new LoggedEventImpl();
    final var entry =
        new MutableLogAppendEntry()
            .key(1)
            .recordMetadata(wrapString("metadata"))
            .recordValue(wrapString("value"));

    // when - as we still use the LoggedEvent to "read" things, it expects to find the event at a
    // specific offset
    final var bytesWritten = serializer.serialize(writeBuffer, writeBufferOffset, entry, 2, 3, 4);
    commitWrite(bytesWritten);

    // then
    event.wrap(writeBuffer, 0);
    assertThat(event.getKey()).isEqualTo(1);
    assertThat(event.getPosition()).isEqualTo(2);
    assertThat(event.getSourceEventPosition()).isEqualTo(3);
    assertThat(event.getTimestamp()).isEqualTo(4);
    assertThat(readString(event::readMetadata)).isEqualTo("metadata");
    assertThat(readString(event::readValue)).isEqualTo("value");
  }

  @Test
  void shouldSkipMetadataIfEmpty() {
    // given
    final var serializer = new LogAppendEntrySerializer();
    final var event = new LoggedEventImpl();
    final var entry = new MutableLogAppendEntry().recordValue(wrapString("value"));

    // when
    final var bytesWritten = serializer.serialize(writeBuffer, writeBufferOffset, entry, 2, 3, 4);
    commitWrite(bytesWritten);

    // then
    event.wrap(writeBuffer, 0);
    assertThat(event.getMetadataLength()).isEqualTo((short) 0);
    assertThat(readString(event::readValue)).isEqualTo("value");
  }

  @Test
  void shouldFailWithAnEmptyValue() {
    // given
    final var serializer = new LogAppendEntrySerializer();
    final var entry = new MutableLogAppendEntry();

    // when - then
    assertThatCode(() -> serializer.serialize(writeBuffer, writeBufferOffset, entry, 2, 3, 4))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldFailWithANegativeTimestamp() {
    // given
    final var serializer = new LogAppendEntrySerializer();
    final var entry = new MutableLogAppendEntry().recordValue(wrapString("value"));

    // when - then
    assertThatCode(() -> serializer.serialize(writeBuffer, writeBufferOffset, entry, 2, 3, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldFailWithANegativePosition() {
    // given
    final var serializer = new LogAppendEntrySerializer();
    final var entry = new MutableLogAppendEntry().recordValue(wrapString("value"));

    // when - then
    assertThatCode(() -> serializer.serialize(writeBuffer, writeBufferOffset, entry, -1, 3, 4))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * Simulates the dispatcher's commit method - this should also be removed once we are rid of the
   * dispatcher
   */
  private void commitWrite(final int bytesWritten) {
    writeBuffer.putInt(
        DataFrameDescriptor.lengthOffset(0), framedLength(bytesWritten), Protocol.ENDIANNESS);
  }

  private String readString(final Consumer<BufferReader> reader) {
    final var value = new StringReader();
    reader.accept(value);
    return value.value;
  }

  private static final class StringReader implements BufferReader {
    private String value;

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      value = BufferUtil.bufferAsString(buffer, offset, length);
    }
  }
}

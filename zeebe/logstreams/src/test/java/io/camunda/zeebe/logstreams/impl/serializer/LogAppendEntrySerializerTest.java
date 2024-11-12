/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.serializer;

import static io.camunda.zeebe.logstreams.util.TestEntry.TestEntryAssert.assertThatEntry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.logstreams.impl.log.LoggedEventImpl;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.util.TestEntry;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

final class LogAppendEntrySerializerTest {
  private final MutableDirectBuffer writeBuffer = new ExpandableArrayBuffer();

  @Test
  void shouldSerializeEntry() {
    // given
    final var event = new LoggedEventImpl();
    final var entry = TestEntry.ofKey(1);

    // when
    LogAppendEntrySerializer.serialize(writeBuffer, 0, entry, 2, 3, 4);

    // then
    event.wrap(writeBuffer, 0);
    assertThatEntry(entry).matchesLoggedEvent(event);
    assertThat(event.getVersion()).isEqualTo((short) 1);
    assertThat(event.getKey()).isEqualTo(1);
    assertThat(event.getPosition()).isEqualTo(2);
    assertThat(event.getSourceEventPosition()).isEqualTo(3);
    assertThat(event.getTimestamp()).isEqualTo(4);
    assertThat(event.shouldSkipProcessing()).isFalse();
  }

  @Test
  void shouldMarkEntryAsProcessed() {
    // given
    final var event = new LoggedEventImpl();
    final var entry = TestEntry.ofKey(1);
    final var processedEntry = LogAppendEntry.ofProcessed(entry);

    // when
    LogAppendEntrySerializer.serialize(writeBuffer, 0, processedEntry, 2, 3, 4);

    // then
    event.wrap(writeBuffer, 0);
    assertThatEntry(entry).matchesLoggedEvent(event);
    assertThat(event.getKey()).isEqualTo(1);
    assertThat(event.getPosition()).isEqualTo(2);
    assertThat(event.getSourceEventPosition()).isEqualTo(3);
    assertThat(event.getTimestamp()).isEqualTo(4);
    assertThat(event.shouldSkipProcessing()).isTrue();
  }

  @Test
  void shouldFailWithEmptyMetadata() {
    // given
    final var entry = TestEntry.builder().withRecordMetadata(null).build();

    // then
    assertThatCode(() -> LogAppendEntrySerializer.serialize(writeBuffer, 0, entry, 2, 3, 4))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldFailWithAnEmptyValue() {
    // given
    final var entry = TestEntry.builder().withRecordValue(null).build();

    // when - then
    assertThatCode(() -> LogAppendEntrySerializer.serialize(writeBuffer, 0, entry, 2, 3, 4))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldFailWithANegativeTimestamp() {
    // given
    final var entry = TestEntry.ofDefaults();

    // when - then
    assertThatCode(() -> LogAppendEntrySerializer.serialize(writeBuffer, 0, entry, 2, 3, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldFailWithANegativePosition() {
    // given
    final var entry = TestEntry.ofDefaults();

    // when - then
    assertThatCode(() -> LogAppendEntrySerializer.serialize(writeBuffer, 0, entry, -1, 3, 4))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/15989")
  void shouldWriteLargeMetadata() {
    // given
    final var rejection = "foo".repeat(Short.MAX_VALUE * 2);
    final var entry = TestEntry.builder().withRecordValue(new TestValue().setFoo("bar")).build();
    final var event = new LoggedEventImpl();
    final var metadata = new RecordMetadata();
    final var value = new TestValue();
    entry.recordMetadata().rejectionReason(rejection);

    // when
    final var serializedLength =
        LogAppendEntrySerializer.serialize(
            writeBuffer, 0, entry, 0, -1, System.currentTimeMillis());
    event.wrap(writeBuffer, 0);
    event.readMetadata(metadata);
    event.readValue(value);

    // then
    assertThat(serializedLength).isGreaterThan(Short.MAX_VALUE);
    assertThat(metadata.getRejectionReason()).isEqualTo(rejection);
    assertThat(value.getFoo()).isEqualTo("bar");
  }

  private static final class TestValue extends UnifiedRecordValue {

    private final StringProperty foo = new StringProperty("foo");

    private TestValue() {
      super(1);
      declareProperty(foo);
    }

    private String getFoo() {
      return BufferUtil.bufferAsString(foo.getValue());
    }

    private TestValue setFoo(final String value) {
      foo.setValue(value);
      return this;
    }
  }
}

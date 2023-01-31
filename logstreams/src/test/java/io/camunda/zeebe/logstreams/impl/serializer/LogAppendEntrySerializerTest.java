/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.serializer;

import static io.camunda.zeebe.logstreams.util.TestEntry.TestEntryAssert.assertThatEntry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.logstreams.impl.log.LoggedEventImpl;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.util.TestEntry;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

final class LogAppendEntrySerializerTest {
  private final MutableDirectBuffer writeBuffer = new ExpandableArrayBuffer();

  @Test
  void shouldSerializeEntry() {
    // given
    final var serializer = new LogAppendEntrySerializer();
    final var event = new LoggedEventImpl();
    final var entry = TestEntry.ofKey(1);

    // when
    serializer.serialize(writeBuffer, 0, entry, 2, 3, 4);

    // then
    event.wrap(writeBuffer, 0);
    assertThatEntry(entry).matchesLoggedEvent(event);
    assertThat(event.getKey()).isEqualTo(1);
    assertThat(event.getPosition()).isEqualTo(2);
    assertThat(event.getSourceEventPosition()).isEqualTo(3);
    assertThat(event.getTimestamp()).isEqualTo(4);
    assertThat(event.isProcessed()).isFalse();
  }

  @Test
  void shouldMarkEntryAsProcessed() {
    // given
    final var serializer = new LogAppendEntrySerializer();
    final var event = new LoggedEventImpl();
    final var entry = TestEntry.ofKey(1);
    final var processedEntry = LogAppendEntry.ofProcessed(entry);

    // when
    serializer.serialize(writeBuffer, 0, processedEntry, 2, 3, 4);

    // then
    event.wrap(writeBuffer, 0);
    assertThatEntry(entry).matchesLoggedEvent(event);
    assertThat(event.getKey()).isEqualTo(1);
    assertThat(event.getPosition()).isEqualTo(2);
    assertThat(event.getSourceEventPosition()).isEqualTo(3);
    assertThat(event.getTimestamp()).isEqualTo(4);
    assertThat(event.isProcessed()).isTrue();
  }

  @Test
  void shouldFailWithEmptyMetadata() {
    // given
    final var serializer = new LogAppendEntrySerializer();
    final var entry = TestEntry.builder().withRecordMetadata(null).build();

    // then
    assertThatCode(() -> serializer.serialize(writeBuffer, 0, entry, 2, 3, 4))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldFailWithAnEmptyValue() {
    // given
    final var serializer = new LogAppendEntrySerializer();
    final var entry = TestEntry.builder().withRecordValue(null).build();

    // when - then
    assertThatCode(() -> serializer.serialize(writeBuffer, 0, entry, 2, 3, 4))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldFailWithANegativeTimestamp() {
    // given
    final var serializer = new LogAppendEntrySerializer();
    final var entry = TestEntry.ofDefaults();

    // when - then
    assertThatCode(() -> serializer.serialize(writeBuffer, 0, entry, 2, 3, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldFailWithANegativePosition() {
    // given
    final var serializer = new LogAppendEntrySerializer();
    final var entry = TestEntry.ofDefaults();

    // when - then
    assertThatCode(() -> serializer.serialize(writeBuffer, 0, entry, -1, 3, 4))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

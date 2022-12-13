/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.log;

import static io.camunda.zeebe.logstreams.util.TestEntry.TestEntryAssert.assertThatEntry;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor;
import io.camunda.zeebe.logstreams.impl.log.LoggedEventImpl;
import io.camunda.zeebe.logstreams.util.LogStreamReaderRule;
import io.camunda.zeebe.logstreams.util.LogStreamRule;
import io.camunda.zeebe.logstreams.util.TestEntry;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class LogStreamBatchWriterTest {
  private final LogStreamRule logStreamRule = LogStreamRule.startByDefault();
  private final LogStreamReaderRule readerRule = new LogStreamReaderRule(logStreamRule);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(logStreamRule).around(readerRule);
  private LogStreamBatchWriter writer;

  @Before
  public void setUp() {
    writer = logStreamRule.getLogStream().newLogStreamBatchWriter();
  }

  private List<LoggedEvent> getWrittenEvents(final long position) {
    final List<LoggedEvent> events = new ArrayList<>();

    assertThat(position).isGreaterThan(0);

    logStreamRule.getLogStream().awaitPositionWritten(position);
    long eventPosition = -1L;

    while (eventPosition < position) {
      final LoggedEventImpl event = (LoggedEventImpl) readerRule.nextEvent();

      final LoggedEventImpl eventCopy = new LoggedEventImpl();
      final DirectBuffer bufferCopy = BufferUtil.cloneBuffer(event.getBuffer());

      eventCopy.wrap(bufferCopy, event.getFragmentOffset());
      events.add(eventCopy);

      eventPosition = event.getPosition();
    }

    assertThat(eventPosition)
        .withFailMessage("No written event found at position: {}", position)
        .isEqualTo(position);

    return events;
  }

  private DirectBuffer getValueBuffer(final LoggedEvent event) {
    final DirectBuffer buffer = event.getValueBuffer();
    final int offset = event.getValueOffset();
    final int length = event.getValueLength();

    return new UnsafeBuffer(buffer, offset, length);
  }

  private DirectBuffer getMetadataBuffer(final LoggedEvent event) {
    final DirectBuffer buffer = event.getMetadata();
    final int offset = event.getMetadataOffset();
    final int length = event.getMetadataLength();

    return new UnsafeBuffer(buffer, offset, length);
  }

  @Test
  public void shouldReturnPositionOfSingleEvent() {
    // when
    final var entry = TestEntry.ofDefaults();
    final long position = writer.tryWrite(entry);

    // then
    assertThat(position).isGreaterThan(0);

    final var events = getWrittenEvents(position);
    assertThat(events).hasSize(1);
    final var loggedEntry = events.get(0);
    assertThatEntry(entry).matchesLoggedEvent(loggedEntry);
    assertThat(loggedEntry.getPosition()).isEqualTo(position);
  }

  @Test
  public void shouldReturnPositionOfLastEvent() {
    // when
    final long position = writer.tryWrite(TestEntry.ofKey(1), TestEntry.ofKey(2));

    // then
    assertThat(position).isGreaterThan(0);

    final List<LoggedEvent> events = getWrittenEvents(position);
    assertThat(events).hasSize(2);
    assertThat(events.get(1).getPosition()).isEqualTo(position);
  }

  @Test
  public void shouldWriteEventWithValueBuffer() {
    // when
    final var entries = List.of(TestEntry.ofKey(1), TestEntry.ofKey(2));
    final long position = writer.tryWrite(entries);

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);
    assertThatEntry(entries.get(0)).matchesLoggedEvent(events.get(0));
    assertThatEntry(entries.get(1)).matchesLoggedEvent(events.get(1));
  }

  @Test
  public void shouldWriteEventWithMetadataBuffer() {
    // when
    final var entries =
        List.of(
            TestEntry.builder()
                .withKey(1)
                .withRecordMetadata(new RecordMetadata().valueType(ValueType.JOB))
                .build(),
            TestEntry.builder()
                .withKey(1)
                .withRecordMetadata(new RecordMetadata().valueType(ValueType.PROCESS))
                .build());
    final long position = writer.tryWrite(entries);

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);
    assertThatEntry(entries.get(0)).matchesLoggedEvent(events.get(0));
    assertThatEntry(entries.get(1)).matchesLoggedEvent(events.get(1));
  }

  @Test
  public void shouldWriteEventWithKey() {
    // when
    final long position = writer.tryWrite(TestEntry.ofKey(123L), TestEntry.ofKey(456L));

    // then
    assertThat(getWrittenEvents(position))
        .extracting(LoggedEvent::getKey)
        .containsExactly(123L, 456L);
  }

  @Test
  public void shouldWriteEventWithSourceEvent() {
    // when
    final long position =
        writer.tryWrite(List.of(TestEntry.ofDefaults(), TestEntry.ofDefaults()), 123L);

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);

    assertThat(events.get(0).getSourceEventPosition()).isEqualTo(123L);
    assertThat(events.get(1).getSourceEventPosition()).isEqualTo(123L);
  }

  @Test
  public void shouldWriteEventWithoutSourceEvent() {
    // when
    final long position = writer.tryWrite(TestEntry.ofKey(1), TestEntry.ofKey(2));

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);

    assertThat(events.get(0).getSourceEventPosition()).isEqualTo(-1L);
    assertThat(events.get(1).getSourceEventPosition()).isEqualTo(-1L);
  }

  @Test
  public void shouldNotFailToWriteEventWithoutKey() {
    // when
    final long position =
        writer.tryWrite(
            TestEntry.ofKey(LogEntryDescriptor.KEY_NULL_VALUE),
            TestEntry.ofKey(LogEntryDescriptor.KEY_NULL_VALUE));

    // then
    assertThat(getWrittenEvents(position)).extracting(LoggedEvent::getKey).contains(-1L);
  }

  @Test
  public void shouldNotFailToWriteBatchWithoutEvents() {
    // when
    final long pos = writer.tryWrite();

    // then
    assertThat(pos).isEqualTo(0);
  }

  @Test
  public void shouldFailToWriteOnClosedLogStream() {
    // given
    logStreamRule.getLogStream().close();

    // when
    final long pos = writer.tryWrite(TestEntry.ofDefaults());

    // then
    assertThat(pos).isEqualTo(-1);
  }

  @Test
  public void shouldFailToWriteEventWithoutValue() {
    // when
    final long pos =
        writer.tryWrite(TestEntry.builder().withRecordValue(null).build(), TestEntry.ofDefaults());

    // then
    assertThat(pos).isEqualTo(0);
  }
}

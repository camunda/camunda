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
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.logstreams.util.LogStreamReaderRule;
import io.camunda.zeebe.logstreams.util.LogStreamRule;
import io.camunda.zeebe.logstreams.util.SynchronousLogStream;
import io.camunda.zeebe.logstreams.util.TestEntry;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class LogStreamWriterTest {
  private final LogStreamRule logStreamRule = LogStreamRule.startByDefault();
  private final LogStreamReaderRule readerRule = new LogStreamReaderRule(logStreamRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(logStreamRule).around(readerRule);

  private LogStreamWriter writer;

  @Before
  public void setUp() {
    final SynchronousLogStream logStream = logStreamRule.getLogStream();
    writer = logStream.newLogStreamWriter();
  }

  @After
  public void tearDown() {
    writer = null;
  }

  @Test
  public void shouldFailToWriteBatchWithoutEvents() {
    // when
    final var result = writer.tryWrite(List.of());

    // then
    EitherAssert.assertThat(result).isLeft().left().isEqualTo(WriteFailure.INVALID_ARGUMENT);
  }

  @Test
  public void shouldReturnPositionOfWrittenEvent() {
    // when
    final long position = tryWrite(TestEntry.ofDefaults());

    // then
    assertThat(position).isGreaterThan(0);

    final LoggedEvent event = getWrittenEvent(position);
    assertThat(event.getPosition()).isEqualTo(position);
  }

  @Test
  public void shouldReturnPositionOfLastEvent() {
    // when
    final long position = writer.tryWrite(List.of(TestEntry.ofKey(1), TestEntry.ofKey(2))).get();

    // then
    assertThat(position).isGreaterThan(0);

    final List<LoggedEvent> events = getWrittenEvents(position);
    assertThat(events).hasSize(2);
    assertThat(events.get(1).getPosition()).isEqualTo(position);
  }

  @Test
  public void shouldWriteEventWithValueBuffer() {
    // when
    final var entry = TestEntry.ofDefaults();
    final long position = tryWrite(entry);

    // then
    final LoggedEvent event = getWrittenEvent(position);
    assertThatEntry(entry).matchesLoggedEvent(event);
  }

  @Test
  public void shouldWriteEventWithMetadataBuffer() {
    // when
    final var entry = TestEntry.ofDefaults();
    final long position = tryWrite(entry);

    // then
    final LoggedEvent event = getWrittenEvent(position);
    assertThatEntry(entry).matchesLoggedEvent(event);
  }

  @Test
  public void shouldWriteEventWithMetadataWriter() {
    // when
    final var entry = TestEntry.ofDefaults();
    final long position = tryWrite(entry);

    // then
    final LoggedEvent event = getWrittenEvent(position);
    assertThatEntry(entry).matchesLoggedEvent(event);
  }

  @Test
  public void shouldWriteEventWithKey() {
    // when
    final long position = tryWrite(TestEntry.ofKey(123L));

    // then
    assertThat(getWrittenEvent(position).getKey()).isEqualTo(123L);
  }

  @Test
  public void shouldWriteEventsWithDifferentWriters() {
    // given
    final long firstPosition = tryWrite(TestEntry.ofKey(123L));

    // when
    final SynchronousLogStream logStream = logStreamRule.getLogStream();
    writer = logStream.newLogStreamWriter();
    final long secondPosition = tryWrite(TestEntry.ofKey(124L));

    // then
    assertThat(secondPosition).isGreaterThan(firstPosition);
    assertThat(getWrittenEvent(firstPosition).getKey()).isEqualTo(123L);
    assertThat(getWrittenEvent(secondPosition).getKey()).isEqualTo(124L);
  }

  @Test
  public void shouldCloseAllWritersAndWriteAgain() {
    // given
    final long firstPosition = tryWrite(TestEntry.ofKey(123L));
    logStreamRule.getLogStream().awaitPositionWritten(firstPosition);

    // when
    writer = null;

    final SynchronousLogStream logStream = logStreamRule.getLogStream();
    writer = logStream.newLogStreamWriter();
    final long secondPosition = tryWrite(TestEntry.ofKey(124L));

    // then
    assertThat(secondPosition).isGreaterThan(firstPosition);
    assertThat(getWrittenEvent(firstPosition).getKey()).isEqualTo(123L);
    assertThat(getWrittenEvent(secondPosition).getKey()).isEqualTo(124L);
  }

  @Test
  public void shouldWriteEventWithSourceEvent() {
    // when
    final long position = tryWrite(TestEntry.ofDefaults(), 123L);

    // then
    final LoggedEvent event = getWrittenEvent(position);
    assertThat(event.getSourceEventPosition()).isEqualTo(123L);
  }

  @Test
  public void shouldWriteEventWithoutSourceEvent() {
    // when
    final long position = tryWrite(TestEntry.ofDefaults());

    // then
    final LoggedEvent event = getWrittenEvent(position);
    assertThat(event.getSourceEventPosition()).isEqualTo(-1L);
  }

  @Test
  public void shouldWriteEventWithNullKey() {
    // when
    final long position = tryWrite(TestEntry.ofKey(LogEntryDescriptor.KEY_NULL_VALUE));

    // then
    assertThat(getWrittenEvent(position).getKey()).isEqualTo(LogEntryDescriptor.KEY_NULL_VALUE);
  }

  @Test
  public void shouldWriteNullKeyByDefault() {
    // when
    final long position = tryWrite(TestEntry.ofDefaults());

    // then
    assertThat(getWrittenEvent(position).getKey()).isEqualTo(LogEntryDescriptor.KEY_NULL_VALUE);
  }

  @Test
  public void shouldFailToWriteEventWithoutValue() {
    // when
    final var res = writer.tryWrite(TestEntry.builder().withRecordValue(null).build());

    // then
    EitherAssert.assertThat(res).isLeft();
  }

  private LoggedEvent getWrittenEvent(final long position) {
    assertThat(position).isGreaterThan(0);
    logStreamRule.getLogStream().awaitPositionWritten(position);
    final LoggedEvent event = readerRule.readEventAtPosition(position);

    assertThat(event)
        .withFailMessage("No written event found at position: <%s>", position)
        .isNotNull();

    return event;
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

  // TODO: unclear if this is still necessary, and presumably we have more control with the
  //  replacement so we could get rid of this failsafe
  private long tryWrite(final LogAppendEntry entry) {
    return Awaitility.await("until dispatcher accepts entry")
        .pollInSameThread()
        .until(() -> writer.tryWrite(entry), Either::isRight)
        .get();
  }

  private long tryWrite(final LogAppendEntry entry, final long sourcePosition) {
    return Awaitility.await("until dispatcher accepts entry")
        .pollInSameThread()
        .until(() -> writer.tryWrite(entry, sourcePosition), Either::isRight)
        .get();
  }
}

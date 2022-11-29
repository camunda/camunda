/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.log;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor;
import io.camunda.zeebe.logstreams.impl.log.LoggedEventImpl;
import io.camunda.zeebe.logstreams.util.LogStreamReaderRule;
import io.camunda.zeebe.logstreams.util.LogStreamRule;
import io.camunda.zeebe.logstreams.util.MutableLogAppendEntry;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerRule;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class LogStreamBatchWriterTest {
  private static final DirectBuffer EVENT_VALUE_1 = wrapString("foo");
  private static final DirectBuffer EVENT_VALUE_2 = wrapString("bar");
  private static final DirectBuffer EVENT_METADATA_1 = wrapString("foobar");
  private static final DirectBuffer EVENT_METADATA_2 = wrapString("baz");

  /** used by some test to write to the logstream in an actor thread. */
  @Rule
  public final ControlledActorSchedulerRule writerScheduler = new ControlledActorSchedulerRule();

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
    final long position = writer.tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE_1));

    // then
    assertThat(position).isGreaterThan(0);

    final List<LoggedEvent> events = getWrittenEvents(position);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getPosition()).isEqualTo(position);
  }

  @Test
  public void shouldReturnPositionOfLastEvent() {
    // when
    final long position =
        writer.tryWrite(
            new MutableLogAppendEntry().key(1).recordValue(EVENT_VALUE_1),
            new MutableLogAppendEntry().key(2).recordValue(EVENT_VALUE_2));

    // then
    assertThat(position).isGreaterThan(0);

    final List<LoggedEvent> events = getWrittenEvents(position);
    assertThat(events).hasSize(2);
    assertThat(events.get(1).getPosition()).isEqualTo(position);
  }

  @Test
  public void shouldWriteEventWithValueBuffer() {
    // when
    final long position =
        writer.tryWrite(
            new MutableLogAppendEntry().key(1).recordValue(EVENT_VALUE_1),
            new MutableLogAppendEntry().key(2).recordValue(EVENT_VALUE_2));

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);
    assertThat(getValueBuffer(events.get(0))).isEqualTo(EVENT_VALUE_1);
    assertThat(getValueBuffer(events.get(1))).isEqualTo(EVENT_VALUE_2);
  }

  @Test
  public void shouldWriteEventWithValueBufferPartially() {
    // when
    final long position =
        writer.tryWrite(
            new MutableLogAppendEntry().key(1).recordValue(EVENT_VALUE_1, 1, 2),
            new MutableLogAppendEntry().key(2).recordValue(EVENT_VALUE_2, 1, 2));

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);
    assertThat(getValueBuffer(events.get(0))).isEqualTo(new UnsafeBuffer(EVENT_VALUE_1, 1, 2));
    assertThat(getValueBuffer(events.get(1))).isEqualTo(new UnsafeBuffer(EVENT_VALUE_2, 1, 2));
  }

  @Test
  public void shouldWriteEventWithMetadataBuffer() {
    // when
    final long position =
        writer.tryWrite(
            new MutableLogAppendEntry()
                .key(1)
                .recordMetadata(EVENT_METADATA_1)
                .recordValue(EVENT_VALUE_1),
            new MutableLogAppendEntry()
                .key(2)
                .recordMetadata(EVENT_METADATA_2)
                .recordValue(EVENT_VALUE_2));

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);
    assertThat(getMetadataBuffer(events.get(0))).isEqualTo(EVENT_METADATA_1);
    assertThat(getMetadataBuffer(events.get(1))).isEqualTo(EVENT_METADATA_2);
  }

  @Test
  public void shouldWriteEventWithMetadataBufferPartially() {
    // when
    final long position =
        writer.tryWrite(
            new MutableLogAppendEntry()
                .key(1)
                .recordMetadata(EVENT_METADATA_1, 1, 2)
                .recordValue(EVENT_VALUE_1),
            new MutableLogAppendEntry()
                .key(2)
                .recordMetadata(EVENT_METADATA_2, 1, 2)
                .recordValue(EVENT_VALUE_2));

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);
    assertThat(getMetadataBuffer(events.get(0)))
        .isEqualTo(new UnsafeBuffer(EVENT_METADATA_1, 1, 2));
    assertThat(getMetadataBuffer(events.get(1)))
        .isEqualTo(new UnsafeBuffer(EVENT_METADATA_2, 1, 2));
  }

  @Test
  public void shouldWriteEventWithKey() {
    // when
    final long position =
        writer.tryWrite(
            new MutableLogAppendEntry().key(123L).recordValue(EVENT_VALUE_1),
            new MutableLogAppendEntry().key(456L).recordValue(EVENT_VALUE_2));

    // then
    assertThat(getWrittenEvents(position))
        .extracting(LoggedEvent::getKey)
        .containsExactly(123L, 456L);
  }

  @Test
  public void shouldWriteEventWithSourceEvent() {
    // when
    final long position =
        writer.tryWrite(
            List.of(
                new MutableLogAppendEntry().key(1).recordValue(EVENT_VALUE_1),
                new MutableLogAppendEntry().key(2).recordValue(EVENT_VALUE_2)),
            123L);

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);

    assertThat(events.get(0).getSourceEventPosition()).isEqualTo(123L);
    assertThat(events.get(1).getSourceEventPosition()).isEqualTo(123L);
  }

  @Test
  public void shouldWriteEventWithoutSourceEvent() {
    // when
    final long position =
        writer.tryWrite(
            new MutableLogAppendEntry().key(1).recordValue(EVENT_VALUE_1),
            new MutableLogAppendEntry().key(2).recordValue(EVENT_VALUE_2));

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);

    assertThat(events.get(0).getSourceEventPosition()).isEqualTo(-1L);
    assertThat(events.get(1).getSourceEventPosition()).isEqualTo(-1L);
  }

  @Test
  public void shouldWriteEventWithTimestamp() throws InterruptedException, ExecutionException {
    // given
    final long timestamp = System.currentTimeMillis() + 10;
    writerScheduler.getClock().setCurrentTime(timestamp);

    // when
    final ActorFuture<Long> position =
        writerScheduler.call(
            () ->
                writer.tryWrite(
                    new MutableLogAppendEntry().key(1).recordValue(EVENT_VALUE_1),
                    new MutableLogAppendEntry().key(2).recordValue(EVENT_VALUE_2)));
    writerScheduler.workUntilDone();

    // then
    assertThat(getWrittenEvents(position.get()))
        .extracting(LoggedEvent::getTimestamp)
        .containsExactly(timestamp, timestamp);
  }

  @Test
  public void shouldNotFailToWriteEventWithoutKey() {
    // when
    final long position =
        writer.tryWrite(
            new MutableLogAppendEntry()
                .key(LogEntryDescriptor.KEY_NULL_VALUE)
                .recordValue(EVENT_VALUE_1),
            new MutableLogAppendEntry()
                .key(LogEntryDescriptor.KEY_NULL_VALUE)
                .recordValue(EVENT_VALUE_2));

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
    final long pos =
        writer.tryWrite(new MutableLogAppendEntry().key(1L).recordValue(EVENT_VALUE_1));

    // then
    assertThat(pos).isEqualTo(-1);
  }
}

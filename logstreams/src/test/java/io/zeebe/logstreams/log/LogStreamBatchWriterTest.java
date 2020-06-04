/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.log;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.logstreams.impl.log.LoggedEventImpl;
import io.zeebe.logstreams.util.LogStreamReaderRule;
import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.logstreams.util.LogStreamWriterRule;
import io.zeebe.logstreams.util.SynchronousLogStream;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public final class LogStreamBatchWriterTest {
  private static final DirectBuffer EVENT_VALUE_1 = wrapString("foo");
  private static final DirectBuffer EVENT_VALUE_2 = wrapString("bar");
  private static final DirectBuffer EVENT_METADATA_1 = wrapString("foobar");
  private static final DirectBuffer EVENT_METADATA_2 = wrapString("baz");

  /** used by some test to write to the logstream in an actor thread. */
  @Rule
  public final ControlledActorSchedulerRule writerScheduler = new ControlledActorSchedulerRule();

  @Rule public ExpectedException expectedException = ExpectedException.none();

  public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  public final LogStreamRule logStreamRule = LogStreamRule.startByDefault(temporaryFolder);
  public final LogStreamReaderRule readerRule = new LogStreamReaderRule(logStreamRule);
  public final LogStreamWriterRule writerRule = new LogStreamWriterRule(logStreamRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(temporaryFolder)
          .around(logStreamRule)
          .around(writerRule)
          .around(readerRule);

  private LogStreamBatchWriter writer;

  @Before
  public void setUp() {
    final SynchronousLogStream logStream = logStreamRule.getLogStream();
    writer = logStream.newLogStreamBatchWriter();
  }

  private List<LoggedEvent> getWrittenEvents(final long position) {
    final List<LoggedEvent> events = new ArrayList<>();

    assertThat(position).isGreaterThan(0);

    writerRule.waitForPositionToBeAppended(position);

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

  private long write(final Consumer<LogStreamBatchWriter> consumer) {
    consumer.accept(writer);
    final Optional<ActorFuture<Long>> optionalFuture =
        TestUtil.doRepeatedly(() -> writer.tryWrite()).until(Optional::isPresent);

    try {
      return optionalFuture.get().join(5, TimeUnit.SECONDS);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private long write(
      final Consumer<LogStreamBatchWriter> consumer, final CompletableFuture<Long> future) {
    consumer.accept(writer);
    final Optional<ActorFuture<Long>> optionalFuture =
        TestUtil.doRepeatedly(() -> writer.tryWrite()).until(Optional::isPresent);

    optionalFuture
        .get()
        .onComplete(
            (pos, t) -> {
              if (t == null) {
                future.complete(pos);
              } else {
                future.completeExceptionally(t);
              }
            });
    return -1L;
  }

  @Test
  public void shouldReturnPositionOfSingleEvent() {
    // when
    final long position = write(w -> w.event().keyNull().value(EVENT_VALUE_1).done());

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
        write(
            w ->
                w.event()
                    .key(1)
                    .value(EVENT_VALUE_1)
                    .done()
                    .event()
                    .key(2)
                    .value(EVENT_VALUE_2)
                    .done());

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
        write(
            w ->
                w.event()
                    .key(1)
                    .value(EVENT_VALUE_1)
                    .done()
                    .event()
                    .key(2)
                    .value(EVENT_VALUE_2)
                    .done());

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);
    assertThat(getValueBuffer(events.get(0))).isEqualTo(EVENT_VALUE_1);
    assertThat(getValueBuffer(events.get(1))).isEqualTo(EVENT_VALUE_2);
  }

  @Test
  public void shouldWriteEventWithValueBufferPartially() {
    // when
    final long position =
        write(
            w ->
                w.event()
                    .key(1)
                    .value(EVENT_VALUE_1, 1, 2)
                    .done()
                    .event()
                    .key(2)
                    .value(EVENT_VALUE_2, 1, 2)
                    .done());

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);
    assertThat(getValueBuffer(events.get(0))).isEqualTo(new UnsafeBuffer(EVENT_VALUE_1, 1, 2));
    assertThat(getValueBuffer(events.get(1))).isEqualTo(new UnsafeBuffer(EVENT_VALUE_2, 1, 2));
  }

  @Test
  public void shouldWriteEventWithValueWriter() {
    // when
    final long position =
        write(
            w ->
                w.event()
                    .key(1)
                    .valueWriter(new DirectBufferWriter().wrap(EVENT_VALUE_1))
                    .done()
                    .event()
                    .key(2)
                    .valueWriter(new DirectBufferWriter().wrap(EVENT_VALUE_2))
                    .done());

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);
    assertThat(getValueBuffer(events.get(0))).isEqualTo(EVENT_VALUE_1);
    assertThat(getValueBuffer(events.get(1))).isEqualTo(EVENT_VALUE_2);
  }

  @Test
  public void shouldWriteEventWithMetadataBuffer() {
    // when
    final long position =
        write(
            w ->
                w.event()
                    .key(1)
                    .value(EVENT_VALUE_1)
                    .metadata(EVENT_METADATA_1)
                    .done()
                    .event()
                    .key(2)
                    .value(EVENT_VALUE_2)
                    .metadata(EVENT_METADATA_2)
                    .done());

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);
    assertThat(getMetadataBuffer(events.get(0))).isEqualTo(EVENT_METADATA_1);
    assertThat(getMetadataBuffer(events.get(1))).isEqualTo(EVENT_METADATA_2);
  }

  @Test
  public void shouldWriteEventWithMetadataBufferPartially() {
    // when
    final long position =
        write(
            w ->
                w.event()
                    .key(1)
                    .value(EVENT_VALUE_1)
                    .metadata(EVENT_METADATA_1, 1, 2)
                    .done()
                    .event()
                    .key(2)
                    .value(EVENT_VALUE_2)
                    .metadata(EVENT_METADATA_2, 1, 2)
                    .done());
    // then
    final List<LoggedEvent> events = getWrittenEvents(position);
    assertThat(getMetadataBuffer(events.get(0)))
        .isEqualTo(new UnsafeBuffer(EVENT_METADATA_1, 1, 2));
    assertThat(getMetadataBuffer(events.get(1)))
        .isEqualTo(new UnsafeBuffer(EVENT_METADATA_2, 1, 2));
  }

  @Test
  public void shouldWriteEventWithMetadataWriter() {
    // when
    final long position =
        write(
            w ->
                w.event()
                    .key(1)
                    .value(EVENT_VALUE_1)
                    .metadataWriter(new DirectBufferWriter().wrap(EVENT_METADATA_1))
                    .done()
                    .event()
                    .key(2)
                    .value(EVENT_VALUE_2)
                    .metadataWriter(new DirectBufferWriter().wrap(EVENT_METADATA_2))
                    .done());

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);
    assertThat(getMetadataBuffer(events.get(0))).isEqualTo(EVENT_METADATA_1);
    assertThat(getMetadataBuffer(events.get(1))).isEqualTo(EVENT_METADATA_2);
  }

  @Test
  public void shouldWriteEventWithKey() {
    // when
    final long position =
        write(
            w ->
                w.event()
                    .key(123L)
                    .value(EVENT_VALUE_1)
                    .done()
                    .event()
                    .key(456L)
                    .value(EVENT_VALUE_2)
                    .done());

    // then
    assertThat(getWrittenEvents(position))
        .extracting(LoggedEvent::getKey)
        .containsExactly(123L, 456L);
  }

  @Test
  public void shouldWriteEventWithSourceEvent() {
    // when
    final long position =
        write(
            w ->
                w.sourceRecordPosition(123L)
                    .event()
                    .key(1)
                    .value(EVENT_VALUE_1)
                    .done()
                    .event()
                    .key(2)
                    .value(EVENT_VALUE_2)
                    .done());

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);

    assertThat(events.get(0).getSourceEventPosition()).isEqualTo(123L);
    assertThat(events.get(1).getSourceEventPosition()).isEqualTo(123L);
  }

  @Test
  public void shouldWriteEventWithoutSourceEvent() {
    // when
    final long position =
        write(
            w ->
                w.event()
                    .key(1)
                    .value(EVENT_VALUE_1)
                    .done()
                    .event()
                    .key(2)
                    .value(EVENT_VALUE_2)
                    .done());

    // then
    final List<LoggedEvent> events = getWrittenEvents(position);

    assertThat(events.get(0).getSourceEventPosition()).isEqualTo(-1L);
    assertThat(events.get(1).getSourceEventPosition()).isEqualTo(-1L);
  }

  @Test
  public void shouldWriteEventWithTimestamp() {
    // given
    final long timestamp = System.currentTimeMillis() + 10;
    writerScheduler.getClock().setCurrentTime(timestamp);
    final CompletableFuture<Long> future = new CompletableFuture<>();

    // when
    writerScheduler.call(
        () ->
            write(
                w ->
                    w.event()
                        .key(1)
                        .value(EVENT_VALUE_1)
                        .done()
                        .event()
                        .key(2)
                        .value(EVENT_VALUE_2)
                        .done(),
                future));
    writerScheduler.workUntilDone();

    // then
    future.whenComplete(
        (pos, t) -> {
          assertThat(t).isNull();
          assertThat(getWrittenEvents(pos))
              .extracting(LoggedEvent::getTimestamp)
              .containsExactly(timestamp, timestamp);
        });
  }

  @Test
  public void shouldNotFailToWriteEventWithoutKey() {
    // when
    final long position =
        write(
            w ->
                w.event()
                    .keyNull()
                    .value(EVENT_VALUE_1)
                    .done()
                    .event()
                    .value(EVENT_VALUE_2)
                    .done());

    // then
    assertThat(getWrittenEvents(position)).extracting(LoggedEvent::getKey).contains(-1L);
  }

  @Test
  public void shouldFailToWriteEventWithoutValue() {
    // when
    assertThatThrownBy(
            () ->
                writer.event().key(1).value(EVENT_VALUE_1).done().event().key(2).done().tryWrite())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("value must not be null");
  }

  @Test
  public void shouldNotFailToWriteBatchWithoutEvents() {
    // when
    final Optional<ActorFuture<Long>> optFuture = writer.tryWrite();

    // then
    assertThat(optFuture).isPresent();
    assertThat(optFuture.get().join()).isEqualTo(0);
  }

  @Test
  public void shouldFailToWriteOnClosedLogStream() {
    // given
    logStreamRule.getLogStream().close();

    // when
    final Optional<ActorFuture<Long>> optFuture =
        writer.event().key(1).value(EVENT_VALUE_1).done().tryWrite();

    // then
    assertThat(optFuture).isEmpty();
  }
}

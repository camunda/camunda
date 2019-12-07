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

import io.zeebe.logstreams.impl.log.LogEntryDescriptor;
import io.zeebe.logstreams.util.LogStreamReaderRule;
import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.logstreams.util.LogStreamWriterRule;
import io.zeebe.logstreams.util.SynchronousLogStream;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class LogStreamWriterTest {
  private static final DirectBuffer EVENT_VALUE = wrapString("value");
  private static final DirectBuffer EVENT_METADATA = wrapString("metadata");

  /** used by some test to write to the logstream in an actor thread. */
  @Rule
  public final ControlledActorSchedulerRule writerScheduler = new ControlledActorSchedulerRule();

  @Rule public ExpectedException expectedException = ExpectedException.none();

  public TemporaryFolder temporaryFolder = new TemporaryFolder();
  public LogStreamRule logStreamRule = LogStreamRule.startByDefault(temporaryFolder);
  public LogStreamReaderRule readerRule = new LogStreamReaderRule(logStreamRule);
  public LogStreamWriterRule writerRule = new LogStreamWriterRule(logStreamRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(temporaryFolder)
          .around(logStreamRule)
          .around(writerRule)
          .around(readerRule);

  private LogStreamRecordWriter writer;

  @Before
  public void setUp() {
    final SynchronousLogStream logStream = logStreamRule.getLogStream();
    writer = logStream.newLogStreamRecordWriter();
  }

  private LoggedEvent getWrittenEvent(final long position) {
    assertThat(position).isGreaterThan(0);

    writerRule.waitForPositionToBeAppended(position);

    final LoggedEvent event = readerRule.readEventAtPosition(position);

    assertThat(event)
        .withFailMessage("No written event found at position: {}", position)
        .isNotNull();

    return event;
  }

  @Test
  public void shouldReturnPositionOfWrittenEvent() {
    // when
    final long position = writer.value(EVENT_VALUE).tryWrite();

    // then
    assertThat(position).isGreaterThan(0);

    final LoggedEvent event = getWrittenEvent(position);
    assertThat(event.getPosition()).isEqualTo(position);
  }

  @Test
  public void shouldWriteEventWithValueBuffer() {
    // when
    final long position = writer.value(EVENT_VALUE).tryWrite();

    // then
    final LoggedEvent event = getWrittenEvent(position);
    final DirectBuffer valueBuffer = event.getValueBuffer();
    final UnsafeBuffer value =
        new UnsafeBuffer(valueBuffer, event.getValueOffset(), event.getValueLength());

    assertThat(value).isEqualTo(EVENT_VALUE);
  }

  @Test
  public void shouldWriteEventWithValueBufferPartially() {
    // when
    final long position = writer.value(EVENT_VALUE, 1, 2).tryWrite();

    // then
    final LoggedEvent event = getWrittenEvent(position);
    final DirectBuffer valueBuffer = event.getValueBuffer();
    final UnsafeBuffer value =
        new UnsafeBuffer(valueBuffer, event.getValueOffset(), event.getValueLength());

    assertThat(value).isEqualTo(new UnsafeBuffer(EVENT_VALUE, 1, 2));
  }

  @Test
  public void shouldWriteEventWithValueWriter() {
    // when
    final long position = writer.valueWriter(new DirectBufferWriter().wrap(EVENT_VALUE)).tryWrite();

    // then
    final LoggedEvent event = getWrittenEvent(position);
    final DirectBuffer valueBuffer = event.getValueBuffer();
    final UnsafeBuffer value =
        new UnsafeBuffer(valueBuffer, event.getValueOffset(), event.getValueLength());

    assertThat(value).isEqualTo(EVENT_VALUE);
  }

  @Test
  public void shouldWriteEventWithMetadataBuffer() {
    // when
    final long position = writer.value(EVENT_VALUE).metadata(EVENT_METADATA).tryWrite();

    // then
    final LoggedEvent event = getWrittenEvent(position);
    final DirectBuffer metadataBuffer = event.getMetadata();
    final UnsafeBuffer metadata =
        new UnsafeBuffer(metadataBuffer, event.getMetadataOffset(), event.getMetadataLength());

    assertThat(metadata).isEqualTo(EVENT_METADATA);
  }

  @Test
  public void shouldWriteEventWithMetadataBufferPartially() {
    // when
    final long position = writer.value(EVENT_VALUE).metadata(EVENT_METADATA, 1, 2).tryWrite();

    // then
    final LoggedEvent event = getWrittenEvent(position);
    final DirectBuffer metadataBuffer = event.getMetadata();
    final UnsafeBuffer metadata =
        new UnsafeBuffer(metadataBuffer, event.getMetadataOffset(), event.getMetadataLength());

    assertThat(metadata).isEqualTo(new UnsafeBuffer(EVENT_METADATA, 1, 2));
  }

  @Test
  public void shouldWriteEventWithMetadataWriter() {
    // when
    final long position =
        writer
            .value(EVENT_VALUE)
            .metadataWriter(new DirectBufferWriter().wrap(EVENT_METADATA))
            .tryWrite();

    // then
    final LoggedEvent event = getWrittenEvent(position);
    final DirectBuffer metadataBuffer = event.getMetadata();
    final UnsafeBuffer metadata =
        new UnsafeBuffer(metadataBuffer, event.getMetadataOffset(), event.getMetadataLength());

    assertThat(metadata).isEqualTo(EVENT_METADATA);
  }

  @Test
  public void shouldWriteEventWithKey() {
    // when
    final long position = writer.key(123L).value(EVENT_VALUE).tryWrite();

    // then
    assertThat(getWrittenEvent(position).getKey()).isEqualTo(123L);
  }

  @Test
  public void shouldWriteEventWithTimestamp() throws InterruptedException, ExecutionException {
    final Callable<Long> doWrite = () -> writer.keyNull().value(EVENT_VALUE).tryWrite();

    // given
    final long firstTimestamp = System.currentTimeMillis();
    writerScheduler.getClock().setCurrentTime(firstTimestamp);

    // when
    final ActorFuture<Long> firstPosition = writerScheduler.call(doWrite);
    writerScheduler.workUntilDone();

    // then
    assertThat(getWrittenEvent(firstPosition.get()).getTimestamp()).isEqualTo(firstTimestamp);

    // given
    final long secondTimestamp = firstTimestamp + 1_000;
    writerScheduler.getClock().setCurrentTime(secondTimestamp);

    // when
    final ActorFuture<Long> secondPosition = writerScheduler.call(doWrite);
    writerScheduler.workUntilDone();

    // then
    assertThat(getWrittenEvent(secondPosition.get()).getTimestamp()).isEqualTo(secondTimestamp);
  }

  @Test
  public void shouldWriteEventWithSourceEvent() {
    // when
    final long position = writer.value(EVENT_VALUE).sourceRecordPosition(123L).tryWrite();

    // then
    final LoggedEvent event = getWrittenEvent(position);
    assertThat(event.getSourceEventPosition()).isEqualTo(123L);
  }

  @Test
  public void shouldWriteEventWithoutSourceEvent() {
    // when
    final long position = writer.value(EVENT_VALUE).tryWrite();

    // then
    final LoggedEvent event = getWrittenEvent(position);
    assertThat(event.getSourceEventPosition()).isEqualTo(-1L);
  }

  @Test
  public void shouldWriteEventWithNullKey() {
    // when
    final long position = writer.keyNull().value(EVENT_VALUE).tryWrite();

    // then
    assertThat(getWrittenEvent(position).getKey()).isEqualTo(LogEntryDescriptor.KEY_NULL_VALUE);
  }

  @Test
  public void shouldWriteNullKeyByDefault() {
    // when
    final long position = writer.value(EVENT_VALUE).tryWrite();

    // then
    assertThat(getWrittenEvent(position).getKey()).isEqualTo(LogEntryDescriptor.KEY_NULL_VALUE);
  }

  @Test
  public void shouldFailToWriteEventWithoutValue() {
    // when
    final long pos = writer.keyNull().tryWrite();

    // then
    assertThat(pos).isEqualTo(0);
  }
}

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
import io.camunda.zeebe.logstreams.util.LogStreamReaderRule;
import io.camunda.zeebe.logstreams.util.LogStreamRule;
import io.camunda.zeebe.logstreams.util.MutableLogAppendEntry;
import io.camunda.zeebe.logstreams.util.SynchronousLogStream;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class LogStreamWriterTest {
  private static final DirectBuffer EVENT_VALUE = wrapString("value");
  private static final DirectBuffer EVENT_METADATA = wrapString("metadata");
  private final LogStreamRule logStreamRule = LogStreamRule.startByDefault();
  private final LogStreamReaderRule readerRule = new LogStreamReaderRule(logStreamRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(logStreamRule).around(readerRule);

  private LogStreamWriter writer;

  @Before
  public void setUp() {
    final SynchronousLogStream logStream = logStreamRule.getLogStream();
    writer = logStream.newLogStreamWriter();
    new MutableLogAppendEntry().reset();
  }

  @After
  public void tearDown() {
    writer = null;
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

  @Test
  public void shouldReturnPositionOfWrittenEvent() {
    // when
    final long position = tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));

    // then
    assertThat(position).isGreaterThan(0);

    final LoggedEvent event = getWrittenEvent(position);
    assertThat(event.getPosition()).isEqualTo(position);
  }

  @Test
  public void shouldWriteEventWithValueBuffer() {
    // when
    final long position = tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));

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
    final long position = tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE, 1, 2));

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
    final long position = tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));

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
    final long position =
        tryWrite(
            new MutableLogAppendEntry().recordMetadata(EVENT_METADATA).recordValue(EVENT_VALUE));

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
    final long position =
        tryWrite(
            new MutableLogAppendEntry()
                .recordMetadata(EVENT_METADATA, 1, 2)
                .recordValue(EVENT_VALUE));

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
        tryWrite(
            new MutableLogAppendEntry().recordMetadata(EVENT_METADATA).recordValue(EVENT_VALUE));

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
    final long position = tryWrite(new MutableLogAppendEntry().key(123L).recordValue(EVENT_VALUE));

    // then
    assertThat(getWrittenEvent(position).getKey()).isEqualTo(123L);
  }

  @Test
  public void shouldWriteEventsWithDifferentWriters() {
    // given
    final long firstPosition =
        tryWrite(new MutableLogAppendEntry().key(123L).recordValue(EVENT_VALUE));

    // when
    final SynchronousLogStream logStream = logStreamRule.getLogStream();
    writer = logStream.newLogStreamWriter();
    final long secondPosition =
        tryWrite(new MutableLogAppendEntry().reset().key(124L).recordValue(EVENT_VALUE));

    // then
    assertThat(secondPosition).isGreaterThan(firstPosition);
    assertThat(getWrittenEvent(firstPosition).getKey()).isEqualTo(123L);
    assertThat(getWrittenEvent(secondPosition).getKey()).isEqualTo(124L);
  }

  @Test
  public void shouldCloseAllWritersAndWriteAgain() {
    // given
    final long firstPosition =
        tryWrite(new MutableLogAppendEntry().key(123L).recordValue(EVENT_VALUE));
    logStreamRule.getLogStream().awaitPositionWritten(firstPosition);

    // when
    writer = null;

    final SynchronousLogStream logStream = logStreamRule.getLogStream();
    writer = logStream.newLogStreamWriter();
    final long secondPosition =
        tryWrite(new MutableLogAppendEntry().reset().key(124L).recordValue(EVENT_VALUE));

    // then
    assertThat(secondPosition).isGreaterThan(firstPosition);
    assertThat(getWrittenEvent(firstPosition).getKey()).isEqualTo(123L);
    assertThat(getWrittenEvent(secondPosition).getKey()).isEqualTo(124L);
  }

  @Test
  public void shouldWriteEventWithSourceEvent() {
    // when
    final long position = tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE), 123L);

    // then
    final LoggedEvent event = getWrittenEvent(position);
    assertThat(event.getSourceEventPosition()).isEqualTo(123L);
  }

  @Test
  public void shouldWriteEventWithoutSourceEvent() {
    // when
    final long position = tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));

    // then
    final LoggedEvent event = getWrittenEvent(position);
    assertThat(event.getSourceEventPosition()).isEqualTo(-1L);
  }

  @Test
  public void shouldWriteEventWithNullKey() {
    // when
    final long position =
        tryWrite(
            new MutableLogAppendEntry()
                .key(LogEntryDescriptor.KEY_NULL_VALUE)
                .recordValue(EVENT_VALUE));

    // then
    assertThat(getWrittenEvent(position).getKey()).isEqualTo(LogEntryDescriptor.KEY_NULL_VALUE);
  }

  @Test
  public void shouldWriteNullKeyByDefault() {
    // when
    final long position = tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));

    // then
    assertThat(getWrittenEvent(position).getKey()).isEqualTo(LogEntryDescriptor.KEY_NULL_VALUE);
  }

  @Test
  public void shouldFailToWriteEventWithoutValue() {
    // when
    final long pos = tryWrite(new MutableLogAppendEntry().reset());

    // then
    assertThat(pos).isEqualTo(0);
  }

  // TODO: unclear if this is still necessary, and presumably we have more control with the
  //  replacement so we could get rid of this failsafe
  private long tryWrite(final LogAppendEntry entry) {
    return Awaitility.await("until dispatcher accepts entry")
        .pollInSameThread()
        .until(() -> writer.tryWrite(entry), p -> p >= 0);
  }

  private long tryWrite(final LogAppendEntry entry, final long sourcePosition) {
    return Awaitility.await("until dispatcher accepts entry")
        .pollInSameThread()
        .until(() -> writer.tryWrite(entry, sourcePosition), p -> p >= 0);
  }
}

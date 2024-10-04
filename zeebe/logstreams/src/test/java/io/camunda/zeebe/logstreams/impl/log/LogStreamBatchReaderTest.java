/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.logstreams.log.LogStreamBatchReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.util.LogStreamReaderRule;
import io.camunda.zeebe.logstreams.util.LogStreamRule;
import io.camunda.zeebe.logstreams.util.TestEntry;
import io.camunda.zeebe.util.ByteValue;
import java.util.NoSuchElementException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class LogStreamBatchReaderTest {

  private static final int LOG_SEGMENT_SIZE = (int) ByteValue.ofMegabytes(4);

  private final LogStreamRule logStreamRule =
      LogStreamRule.startByDefault(builder -> builder.withMaxFragmentSize(LOG_SEGMENT_SIZE));

  private final LogStreamReaderRule readerRule = new LogStreamReaderRule(logStreamRule);

  @Rule public final RuleChain ruleChain = RuleChain.outerRule(logStreamRule).around(readerRule);

  private LogStreamBatchReader batchReader;
  private LogStreamWriter writer;

  @Before
  public void setUp() {
    final var logStreamReader = readerRule.getLogStreamReader();
    batchReader = new LogStreamBatchReaderImpl(logStreamReader);
    writer = logStreamRule.getLogStream().newBlockingLogStreamWriter();
  }

  @Test
  public void shouldNotHaveNextIfEmpty() {
    assertThat(batchReader.hasNext()).isFalse();
  }

  @Test
  public void shouldReadEventsInBatch() {
    // given
    final long eventPosition1 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofKey(1L), 1L).get();
    final long eventPosition2 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofKey(2L), 1L).get();

    // then
    assertThat(batchReader.hasNext()).isTrue();

    final var batch = batchReader.next();
    assertThat(batch).isNotNull();

    assertThat(batch.hasNext()).isTrue();
    final var event1 = batch.next();
    assertThat(event1.getKey()).isEqualTo(1L);
    assertThat(event1.getPosition()).isEqualTo(eventPosition1);

    assertThat(batch.hasNext()).isTrue();
    final var event2 = batch.next();
    assertThat(event2.getKey()).isEqualTo(2L);
    assertThat(event2.getPosition()).isEqualTo(eventPosition2);

    assertThat(batch.hasNext()).isFalse();
    assertThat(batchReader.hasNext()).isFalse();
  }

  @Test
  public void shouldReadNextBatch() {
    // given
    final long eventPosition1 = writer.tryWrite(WriteContext.internal(), TestEntry.ofKey(1L)).get();
    final long eventPosition2 = writer.tryWrite(WriteContext.internal(), TestEntry.ofKey(2L)).get();

    // then
    assertThat(batchReader.hasNext()).isTrue();
    final var batch1 = batchReader.next();

    assertThat(batch1.hasNext()).isTrue();
    final var event1 = batch1.next();
    assertThat(event1.getKey()).isEqualTo(1L);
    assertThat(event1.getPosition()).isEqualTo(eventPosition1);

    assertThat(batch1.hasNext()).isFalse();
    assertThat(batchReader.hasNext()).isTrue();

    final var batch2 = batchReader.next();
    assertThat(batch2.hasNext()).isTrue();
    final var event2 = batch2.next();
    assertThat(event2.getKey()).isEqualTo(2L);
    assertThat(event2.getPosition()).isEqualTo(eventPosition2);

    assertThat(batch2.hasNext()).isFalse();
    assertThat(batchReader.hasNext()).isFalse();
  }

  @Test
  public void shouldReadEventsWithoutSourceEventPosition() {
    // given
    final long eventPosition1 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults()).get();
    final long eventPosition2 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults()).get();

    // then
    assertThat(batchReader.hasNext()).isTrue();

    final var batch1 = batchReader.next();
    assertThat(batch1.hasNext()).isTrue();
    assertThat(batch1.next().getPosition()).isEqualTo(eventPosition1);

    assertThat(batch1.hasNext()).isFalse();
    assertThat(batchReader.hasNext()).isTrue();

    final var batch2 = batchReader.next();
    assertThat(batch2.hasNext()).isTrue();
    assertThat(batch2.next().getPosition()).isEqualTo(eventPosition2);
  }

  @Test
  public void shouldNotIncludeEventsWithoutSourceEventPosition() {
    // given
    final long eventPosition1 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults()).get();
    final long eventPosition2 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults()).get();
    final long eventPosition3 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults()).get();

    // then
    assertThat(batchReader.hasNext()).isTrue();

    final var batch1 = batchReader.next();
    assertThat(batch1.hasNext()).isTrue();
    assertThat(batch1.next().getPosition()).isEqualTo(eventPosition1);

    assertThat(batch1.hasNext()).isFalse();
    assertThat(batchReader.hasNext()).isTrue();

    final var batch2 = batchReader.next();
    assertThat(batch2.hasNext()).isTrue();
    assertThat(batch2.next().getPosition()).isEqualTo(eventPosition2);

    assertThat(batch2.hasNext()).isFalse();
    assertThat(batchReader.hasNext()).isTrue();

    final var batch3 = batchReader.next();
    assertThat(batch3.hasNext()).isTrue();
    assertThat(batch3.next().getPosition()).isEqualTo(eventPosition3);
  }

  @Test
  public void shouldMoveBatchToHead() {
    // given
    final long eventPosition1 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults(), 1L).get();
    final long eventPosition2 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults(), 1L).get();
    final long eventPosition3 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults(), 2L).get();

    assertThat(batchReader.hasNext()).isTrue();

    final var batch1 = batchReader.next();
    assertThat(batch1.hasNext()).isTrue();
    assertThat(batch1.next().getPosition()).isEqualTo(eventPosition1);

    // when
    batch1.head();

    // then
    assertThat(batch1.hasNext()).isTrue();
    assertThat(batch1.next().getPosition()).isEqualTo(eventPosition1);

    assertThat(batch1.hasNext()).isTrue();
    assertThat(batch1.next().getPosition()).isEqualTo(eventPosition2);

    assertThat(batch1.hasNext()).isFalse();

    final var batch2 = batchReader.next();
    assertThat(batch2.hasNext()).isTrue();
    assertThat(batch2.next().getPosition()).isEqualTo(eventPosition3);
  }

  @Test
  public void shouldSkipEventsInBatch() {
    // given
    final long eventPosition1 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults(), 1L).get();
    writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults(), 1L);
    final long eventPosition3 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults(), 2L).get();

    assertThat(batchReader.hasNext()).isTrue();

    final var batch1 = batchReader.next();
    assertThat(batch1.hasNext()).isTrue();
    assertThat(batch1.next().getPosition()).isEqualTo(eventPosition1);

    // when
    assertThat(batchReader.hasNext()).isTrue();

    final var batch2 = batchReader.next();
    assertThat(batch2.hasNext()).isTrue();
    assertThat(batch2.next().getPosition()).isEqualTo(eventPosition3);
  }

  @Test
  public void shouldSeekToHeadIfNegative() {
    // given
    final long eventPosition1 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults()).get();
    writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults());

    // when
    final var found = batchReader.seekToNextBatch(-1L);

    // then
    assertThat(found).isTrue();
    assertThat(batchReader.hasNext()).isTrue();

    final var batch = batchReader.next();
    assertThat(batch.hasNext()).isTrue();
    assertThat(batch.next().getPosition()).isEqualTo(eventPosition1);
  }

  @Test
  public void shouldSeekToNextBatch() {
    // given
    writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults());
    final long eventPosition2 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults()).get();
    final long eventPosition3 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults()).get();

    // when
    final var found = batchReader.seekToNextBatch(eventPosition2);

    // then
    assertThat(found).isTrue();
    assertThat(batchReader.hasNext()).isTrue();

    final var batch = batchReader.next();
    assertThat(batch.hasNext()).isTrue();
    assertThat(batch.next().getPosition()).isEqualTo(eventPosition3);
  }

  @Test
  public void shouldSeekToNextEventWithinBatch() {
    // given
    final long eventPosition1 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults(), 1L).get();
    writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults(), 1L);
    final long eventPosition3 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults(), 2L).get();

    // when
    final var found = batchReader.seekToNextBatch(eventPosition1);

    // then
    assertThat(found).isTrue();
    assertThat(batchReader.hasNext()).isTrue();

    final var batch = batchReader.next();
    assertThat(batch.hasNext()).isTrue();
    assertThat(batch.next().getPosition()).isEqualTo(eventPosition3);
  }

  @Test
  public void shouldSeekToTailIfLastEvent() {
    // given
    writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults());
    final long eventPosition2 =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults()).get();

    // when
    final var found = batchReader.seekToNextBatch(eventPosition2);

    // then
    assertThat(found).isTrue();
    assertThat(batchReader.hasNext()).isFalse();
  }

  @Test
  public void shouldSeekToNotExistingPosition() {
    // given
    final var eventPosition =
        writer.tryWrite(WriteContext.internal(), TestEntry.ofDefaults()).get();

    // when
    final var found = batchReader.seekToNextBatch(eventPosition + 1);

    // then
    assertThat(found).isFalse();
    assertThat(batchReader.hasNext()).isFalse();
  }

  @Test
  public void shouldNotHaveNextIfClosed() {
    // given
    batchReader.close();

    // when - then
    assertThat(batchReader.hasNext()).isFalse();
  }

  @Test
  public void shouldThrowNoSuchElementExceptionOnNextBatch() {
    // given
    assertThat(batchReader.hasNext()).isFalse();

    // when / then
    assertThatThrownBy(batchReader::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  public void shouldThrowNoSuchElementExceptionOnNextEvent() {
    // given
    writer.tryWrite(WriteContext.internal(), TestEntry.ofKey(1));

    assertThat(batchReader.hasNext()).isTrue();

    final var batch = batchReader.next();
    batch.next();

    assertThat(batch.hasNext()).isFalse();

    // when / then
    assertThatThrownBy(batch::next).isInstanceOf(NoSuchElementException.class);
  }
}

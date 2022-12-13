/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.logstreams.log.LogStreamBatchReader;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
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
  private LogStreamBatchWriter batchWriter;

  @Before
  public void setUp() {
    final var logStreamReader = readerRule.getLogStreamReader();
    batchReader = new LogStreamBatchReaderImpl(logStreamReader);
    batchWriter = logStreamRule.getLogStreamBatchWriter();
  }

  @Test
  public void shouldNotHaveNextIfEmpty() {
    assertThat(batchReader.hasNext()).isFalse();
  }

  @Test
  public void shouldReadEventsInBatch() {
    // given
    final long eventPosition1 = batchWriter.tryWrite(TestEntry.ofKey(1L), 1L);
    final long eventPosition2 = batchWriter.tryWrite(TestEntry.ofKey(2L), 1L);

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
    final long eventPosition1 = batchWriter.tryWrite(TestEntry.ofKey(1L));
    final long eventPosition2 = batchWriter.tryWrite(TestEntry.ofKey(2L));

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
    final long eventPosition1 = batchWriter.tryWrite(TestEntry.ofDefaults());
    final long eventPosition2 = batchWriter.tryWrite(TestEntry.ofDefaults());

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
    final long eventPosition1 = batchWriter.tryWrite(TestEntry.ofDefaults());
    final long eventPosition2 = batchWriter.tryWrite(TestEntry.ofDefaults());
    final long eventPosition3 = batchWriter.tryWrite(TestEntry.ofDefaults());

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
    final long eventPosition1 = batchWriter.tryWrite(TestEntry.ofDefaults(), 1L);
    final long eventPosition2 = batchWriter.tryWrite(TestEntry.ofDefaults(), 1L);
    final long eventPosition3 = batchWriter.tryWrite(TestEntry.ofDefaults(), 2L);

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
    final long eventPosition1 = batchWriter.tryWrite(TestEntry.ofDefaults(), 1L);
    batchWriter.tryWrite(TestEntry.ofDefaults(), 1L);
    final long eventPosition3 = batchWriter.tryWrite(TestEntry.ofDefaults(), 2L);

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
    final long eventPosition1 = batchWriter.tryWrite(TestEntry.ofDefaults());
    batchWriter.tryWrite(TestEntry.ofDefaults());

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
    batchWriter.tryWrite(TestEntry.ofDefaults());
    final long eventPosition2 = batchWriter.tryWrite(TestEntry.ofDefaults());
    final long eventPosition3 = batchWriter.tryWrite(TestEntry.ofDefaults());

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
    final long eventPosition1 = batchWriter.tryWrite(TestEntry.ofDefaults(), 1L);
    batchWriter.tryWrite(TestEntry.ofDefaults(), 1L);
    final long eventPosition3 = batchWriter.tryWrite(TestEntry.ofDefaults(), 2L);

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
    batchWriter.tryWrite(TestEntry.ofDefaults());
    final long eventPosition2 = batchWriter.tryWrite(TestEntry.ofDefaults());

    // when
    final var found = batchReader.seekToNextBatch(eventPosition2);

    // then
    assertThat(found).isTrue();
    assertThat(batchReader.hasNext()).isFalse();
  }

  @Test
  public void shouldSeekToNotExistingPosition() {
    // given
    final var eventPosition = batchWriter.tryWrite(TestEntry.ofDefaults());

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
    batchWriter.tryWrite(TestEntry.ofKey(1));

    assertThat(batchReader.hasNext()).isTrue();

    final var batch = batchReader.next();
    batch.next();

    assertThat(batch.hasNext()).isFalse();

    // when / then
    assertThatThrownBy(batch::next).isInstanceOf(NoSuchElementException.class);
  }
}

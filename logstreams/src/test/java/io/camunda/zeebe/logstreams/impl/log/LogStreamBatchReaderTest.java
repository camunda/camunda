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
import io.camunda.zeebe.logstreams.util.LogStreamReaderRule;
import io.camunda.zeebe.logstreams.util.LogStreamRule;
import io.camunda.zeebe.logstreams.util.LogStreamWriterRule;
import io.camunda.zeebe.util.ByteValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.NoSuchElementException;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class LogStreamBatchReaderTest {

  private static final DirectBuffer EVENT_VALUE = BufferUtil.wrapString("test");
  private static final int LOG_SEGMENT_SIZE = (int) ByteValue.ofMegabytes(4);

  private final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final LogStreamRule logStreamRule =
      LogStreamRule.startByDefault(
          temporaryFolder,
          builder -> builder.withMaxFragmentSize(LOG_SEGMENT_SIZE),
          builder -> builder);

  private final LogStreamWriterRule writerRule = new LogStreamWriterRule(logStreamRule);
  private final LogStreamReaderRule readerRule = new LogStreamReaderRule(logStreamRule);

  @Rule
  public final RuleChain ruleChain =
      RuleChain.outerRule(temporaryFolder)
          .around(logStreamRule)
          .around(readerRule)
          .around(writerRule);

  private LogStreamBatchReader batchReader;

  @Before
  public void setUp() {
    final var logStreamReader = readerRule.getLogStreamReader();
    batchReader = new LogStreamBatchReaderImpl(logStreamReader);
  }

  @Test
  public void shouldNotHaveNextIfEmpty() {
    assertThat(batchReader.hasNext()).isFalse();
  }

  @Test
  public void shouldReadEventsInBatch() {
    // given
    final long eventPosition1 =
        writerRule.sourceEventPosition(1L).writeEvent(w -> w.key(1L).value(EVENT_VALUE));
    final long eventPosition2 =
        writerRule.sourceEventPosition(1L).writeEvent(w -> w.key(2L).value(EVENT_VALUE));

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
    final long eventPosition1 =
        writerRule.sourceEventPosition(1L).writeEvent(w -> w.key(1L).value(EVENT_VALUE));
    final long eventPosition2 =
        writerRule.sourceEventPosition(2L).writeEvent(w -> w.key(2L).value(EVENT_VALUE));

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
    final long eventPosition1 = writerRule.writeEvent(EVENT_VALUE);
    final long eventPosition2 = writerRule.writeEvent(EVENT_VALUE);

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
    final long eventPosition1 = writerRule.sourceEventPosition(1L).writeEvent(EVENT_VALUE);
    final long eventPosition2 = writerRule.writeEvent(EVENT_VALUE);
    final long eventPosition3 = writerRule.sourceEventPosition(2L).writeEvent(EVENT_VALUE);

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
    final long eventPosition1 = writerRule.sourceEventPosition(1L).writeEvent(EVENT_VALUE);
    final long eventPosition2 = writerRule.sourceEventPosition(1L).writeEvent(EVENT_VALUE);
    final long eventPosition3 = writerRule.sourceEventPosition(2L).writeEvent(EVENT_VALUE);

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
    final long eventPosition1 = writerRule.sourceEventPosition(1L).writeEvent(EVENT_VALUE);
    writerRule.sourceEventPosition(1L).writeEvent(EVENT_VALUE);
    final long eventPosition3 = writerRule.sourceEventPosition(2L).writeEvent(EVENT_VALUE);

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
    final long eventPosition1 = writerRule.sourceEventPosition(1L).writeEvent(EVENT_VALUE);
    writerRule.sourceEventPosition(1L).writeEvent(EVENT_VALUE);

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
    writerRule.sourceEventPosition(1L).writeEvent(EVENT_VALUE);
    final long eventPosition2 = writerRule.sourceEventPosition(1L).writeEvent(EVENT_VALUE);
    final long eventPosition3 = writerRule.sourceEventPosition(2L).writeEvent(EVENT_VALUE);

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
    final long eventPosition1 = writerRule.sourceEventPosition(1L).writeEvent(EVENT_VALUE);
    writerRule.sourceEventPosition(1L).writeEvent(EVENT_VALUE);
    final long eventPosition3 = writerRule.sourceEventPosition(2L).writeEvent(EVENT_VALUE);

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
    writerRule.sourceEventPosition(1L).writeEvent(EVENT_VALUE);
    final long eventPosition2 = writerRule.sourceEventPosition(1L).writeEvent(EVENT_VALUE);

    // when
    final var found = batchReader.seekToNextBatch(eventPosition2);

    // then
    assertThat(found).isTrue();
    assertThat(batchReader.hasNext()).isFalse();
  }

  @Test
  public void shouldSeekToNotExistingPosition() {
    // given
    final var eventPosition = writerRule.sourceEventPosition(1L).writeEvent(EVENT_VALUE);

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
    writerRule.writeEvent(w -> w.key(1L).value(EVENT_VALUE));

    assertThat(batchReader.hasNext()).isTrue();

    final var batch = batchReader.next();
    batch.next();

    assertThat(batch.hasNext()).isFalse();

    // when / then
    assertThatThrownBy(batch::next).isInstanceOf(NoSuchElementException.class);
  }
}

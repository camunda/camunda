/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.log;

import static io.zeebe.util.StringUtil.getBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.util.LogStreamReaderRule;
import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.logstreams.util.LogStreamWriterRule;
import io.zeebe.test.util.TestUtil;
import java.util.NoSuchElementException;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class LogStreamReaderTest {
  private static final UnsafeBuffer EVENT_VALUE = new UnsafeBuffer(getBytes("test"));
  private static final UnsafeBuffer BIG_EVENT_VALUE =
      new UnsafeBuffer(new byte[BufferedLogStreamReader.DEFAULT_INITIAL_BUFFER_CAPACITY * 2]);

  @Rule public ExpectedException expectedException = ExpectedException.none();

  public TemporaryFolder temporaryFolder = new TemporaryFolder();
  public LogStreamRule logStreamRule = new LogStreamRule(temporaryFolder);
  public LogStreamWriterRule writer = new LogStreamWriterRule(logStreamRule);
  public LogStreamReaderRule readerRule = new LogStreamReaderRule(logStreamRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(temporaryFolder).around(logStreamRule).around(readerRule).around(writer);

  private LogStreamReader reader;

  @Before
  public void setUp() {
    reader = readerRule.getLogStreamReader();
  }

  @Test
  public void shouldThrowExceptionIteratorNotInitialized() {
    // given
    final LogStreamReader reader = new BufferedLogStreamReader();

    // expect
    expectedException.expectMessage("Iterator not initialized");
    expectedException.expect(IllegalStateException.class);

    // when
    reader.hasNext();
  }

  @Test
  public void shouldThrowExceptionIteratorNotInitializedOnNext() {
    // given
    final LogStreamReader reader = new BufferedLogStreamReader();

    // expect
    expectedException.expectMessage("Iterator not initialized");
    expectedException.expect(IllegalStateException.class);

    // when
    // then
    reader.next();
  }

  @Test
  public void shouldNotHaveNext() {
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldHaveNext() {
    // given
    final long position = writer.writeEvent(EVENT_VALUE);

    // then
    assertThat(reader.hasNext()).isEqualTo(true);
    final LoggedEvent next = reader.next();
    assertThat(next.getKey()).isEqualTo(position);
    assertThat(next.getPosition()).isEqualTo(position);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldThrowNoSuchElementExceptionOnNextCall() {
    // expect
    expectedException.expectMessage(
        "Api protocol violation: No next log entry available; You need to probe with hasNext() first.");
    expectedException.expect(NoSuchElementException.class);

    // when
    // then
    reader.next();
  }

  @Test
  public void shouldReturnPositionOfCurrentLoggedEvent() {
    // given
    final long position = writer.writeEvent(EVENT_VALUE);
    reader.seekToFirstEvent();

    // then
    assertThat(reader.getPosition()).isEqualTo(position);
  }

  @Test
  public void shouldReturnNoPositionIfNotActiveOrInitialized() {
    // given
    writer.writeEvent(EVENT_VALUE);

    // then
    assertThat(reader.getPosition()).isEqualTo(-1);
  }

  @Test
  public void shouldThrowIteratorNotInitializedIfReaderWasClosedAndHasNextIsCalled() {
    // given
    reader.close();
    writer.writeEvent(EVENT_VALUE);

    // expect
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("Iterator not initialized");

    // when
    reader.hasNext();
  }

  @Test
  public void shouldReopenAndReturnLoggedEvent() {
    // given
    reader.close();
    final long position = writer.writeEvent(EVENT_VALUE);
    reader.wrap(logStreamRule.getLogStream());

    // then
    final LoggedEvent loggedEvent = readerRule.nextEvent();
    assertThat(loggedEvent.getKey()).isEqualTo(position);
    assertThat(loggedEvent.getPosition()).isEqualTo(position);
  }

  @Test
  public void shouldReturnUncommittedLoggedEvent() {
    // given
    final BufferedLogStreamReader reader = new BufferedLogStreamReader();

    logStreamRule.setCommitPosition(Long.MIN_VALUE);
    final long position = writer.writeEvent(EVENT_VALUE);
    reader.wrap(logStreamRule.getLogStream());

    // when
    TestUtil.waitUntil(reader::hasNext);

    // then
    final LoggedEvent loggedEvent = reader.next();
    assertThat(loggedEvent.getKey()).isEqualTo(position);
    assertThat(loggedEvent.getPosition()).isEqualTo(position);

    reader.close();
  }

  @Test
  @Ignore
  public void shouldNotReturnLoggedEventUntilCommitted() {
    // given
    final long position = writer.writeEvent(EVENT_VALUE);
    logStreamRule.setCommitPosition(Long.MIN_VALUE);

    // then
    assertThat(reader.hasNext()).isFalse();

    // when
    logStreamRule.setCommitPosition(position);

    // then
    assertThat(reader.hasNext()).isTrue();
    final LoggedEvent loggedEvent = reader.next();
    assertThat(loggedEvent.getKey()).isEqualTo(position);
    assertThat(loggedEvent.getPosition()).isEqualTo(position);
  }

  @Test
  @Ignore
  public void shouldSeekToUncommittedLoggedEventIfFlagIsSet() {
    // given
    final BufferedLogStreamReader reader = new BufferedLogStreamReader();
    final long firstPos = writer.writeEvent(EVENT_VALUE);
    logStreamRule.setCommitPosition(firstPos);
    final long secondPos = writer.writeEvent(EVENT_VALUE);
    reader.wrap(logStreamRule.getLogStream());

    // when
    reader.seek(secondPos);

    // then
    assertThat(reader.hasNext()).isTrue();
    final LoggedEvent loggedEvent = reader.next();
    assertThat(loggedEvent.getKey()).isEqualTo(secondPos);
    assertThat(loggedEvent.getPosition()).isEqualTo(secondPos);

    reader.close();
  }

  @Test
  public void shouldWrapAndSeekToEvent() {
    // given
    writer.writeEvent(EVENT_VALUE);
    final long secondPos = writer.writeEvent(EVENT_VALUE);

    // when
    reader.wrap(logStreamRule.getLogStream(), secondPos);

    // then
    final LoggedEvent loggedEvent = reader.next();
    assertThat(loggedEvent.getKey()).isEqualTo(secondPos);
    assertThat(loggedEvent.getPosition()).isEqualTo(secondPos);

    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldReturnLastEventAfterSeekToLastEvent() {
    // given
    final int eventCount = 10;

    final long lastPosition = writer.writeEvents(eventCount, EVENT_VALUE);

    // when
    reader.seekToLastEvent();

    // then
    assertThat(reader.hasNext()).isTrue();
    final LoggedEvent loggedEvent = reader.next();
    assertThat(loggedEvent.getKey()).isEqualTo(eventCount);
    assertThat(loggedEvent.getPosition()).isEqualTo(lastPosition);

    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldIncreaseBufferAndSeekToLastEventIfSmallAndBigDoesNotFitTogether() {
    // given
    final int eventCount = 3;
    final byte[] bytes = new byte[1024 - 56];
    writer.writeEvents(31, new UnsafeBuffer(bytes));

    // when
    final long lastBigEventPosition = writer.writeEvents(eventCount, BIG_EVENT_VALUE);

    // then
    assertThat(reader.seek(lastBigEventPosition)).isTrue();
    final LoggedEvent bigEvent = reader.next();
    assertThat(bigEvent.getKey()).isEqualTo(eventCount);
    assertThat(bigEvent.getPosition()).isEqualTo(lastBigEventPosition);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldResizeBufferAndIterateOverSmallAndBigLoggedEvent() {
    // given
    final int eventCount = 500;
    final long lastPosition = writer.writeEvents(eventCount, EVENT_VALUE);

    // then
    readerRule.assertEvents(eventCount - 1, EVENT_VALUE);
    assertThat(reader.hasNext()).isTrue();

    // when
    final long bigEventPosition = writer.writeEvent(BIG_EVENT_VALUE);

    // then
    LoggedEvent loggedEvent = readerRule.nextEvent();
    assertThat(loggedEvent.getKey()).isEqualTo(eventCount);
    assertThat(loggedEvent.getPosition()).isEqualTo(lastPosition);

    loggedEvent = readerRule.nextEvent();
    assertThat(loggedEvent.getKey()).isEqualTo(bigEventPosition);
    assertThat(loggedEvent.getPosition()).isEqualTo(bigEventPosition);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldReturnBigLoggedEvent() {
    // given
    final long position = writer.writeEvent(BIG_EVENT_VALUE);

    // then
    final LoggedEvent loggedEvent = readerRule.nextEvent();
    assertThat(loggedEvent.getKey()).isEqualTo(position);
    assertThat(loggedEvent.getPosition()).isEqualTo(position);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldSeekToLastBigLoggedEvents() {
    // given
    final int eventCount = 1000;

    final long lastPosition = writer.writeEvents(eventCount, BIG_EVENT_VALUE);

    // when
    reader.seekToLastEvent();

    // then
    final LoggedEvent loggedEvent = reader.next();
    assertThat(loggedEvent.getKey()).isEqualTo(eventCount);
    assertThat(loggedEvent.getPosition()).isEqualTo(lastPosition);

    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldReturnBigLoggedEvents() {
    // given
    final int eventCount = 1000;

    writer.writeEvents(eventCount, BIG_EVENT_VALUE);

    // then
    readerRule.assertEvents(eventCount, BIG_EVENT_VALUE);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldIterateOverManyEvents() {
    // given
    final int eventCount = 100_000;

    // when
    writer.writeEvents(eventCount, EVENT_VALUE);

    // then
    readerRule.assertEvents(eventCount, EVENT_VALUE);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldIterateMultipleTimes() {
    // given
    final int eventCount = 500;
    writer.writeEvents(eventCount, EVENT_VALUE);

    // when
    reader.seekToFirstEvent();
    readerRule.assertEvents(eventCount, EVENT_VALUE);
    assertThat(reader.hasNext()).isFalse();

    reader.seekToFirstEvent();
    readerRule.assertEvents(eventCount, EVENT_VALUE);
    assertThat(reader.hasNext()).isFalse();

    reader.seekToFirstEvent();
    readerRule.assertEvents(eventCount, EVENT_VALUE);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldLimitAllocate() {
    // mock logStorage to always return insufficient capacity to increase buffer til max
    final LogStream logStream = logStreamRule.getLogStream();
    final LogStorage logStorage = mock(LogStorage.class);
    when(logStorage.read(any(), anyLong(), any()))
        .thenReturn(LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY);

    // then
    expectedException.expect(RuntimeException.class);
    expectedException.expectMessage(
        "Next fragment requires more space then the maximal buffer capacity of "
            + BufferedLogStreamReader.MAX_BUFFER_CAPACITY);

    // when
    ((BufferedLogStreamReader) reader).wrap(logStorage, logStream.getLogBlockIndex());
  }
}

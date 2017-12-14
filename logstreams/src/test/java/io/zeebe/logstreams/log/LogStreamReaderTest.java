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

import io.zeebe.logstreams.LogStreams;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.util.NoSuchElementException;

import static io.zeebe.util.StringUtil.getBytes;
import static org.assertj.core.api.Assertions.assertThat;

public class LogStreamReaderTest
{
    private static final UnsafeBuffer EVENT_VALUE = new UnsafeBuffer(getBytes("test"));
    private static final int WORK_COUNT = 100_000;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private BufferedLogStreamReader reader;
    private LogStream logStream;
    private ActorScheduler actorScheduler;
    private LogStreamWriter writer;

    @Before
    public void setUp()
    {
        reader = new BufferedLogStreamReader();


        actorScheduler = ActorSchedulerBuilder.createDefaultScheduler("test");
        logStream = LogStreams.createFsLogStream(BufferUtil.wrapString("topic"), 0)
                           .logDirectory(temporaryFolder.getRoot().getAbsolutePath())
                           .actorScheduler(actorScheduler)
                           .deleteOnClose(true)
                           .build();
        logStream.open();
        logStream.setCommitPosition(Long.MAX_VALUE);

        writer = new LogStreamWriterImpl(logStream);
    }

    @After
    public void clear()
    {
        logStream.close();
        reader.close();
        actorScheduler.close();
    }

    private long[] writeEvents(int count, DirectBuffer eventValue)
    {
        final long[] positions = new long[count];

        for (int i = 0; i < count; i++)
        {
            positions[i] = writeEvent(i, eventValue);
        }
        return positions;
    }

    private long writeEvent(long key, DirectBuffer eventValue)
    {
        long position = -1;
        while (position <= 0)
        {
            position = writer
            .key(key)
            .value(eventValue)
            .tryWrite();
        }

        return position;
    }

    @Test
    public void shouldThrowExceptionIteratorNotInitialized()
    {
        // expect
        expectedException.expectMessage("Iterator not initialized");
        expectedException.expect(IllegalStateException.class);

        // when
        reader.hasNext();
    }

    @Test
    public void shouldThrowExceptionIteratorNotInitializedOnNext()
    {
        // expect
        expectedException.expectMessage("Iterator not initialized");
        expectedException.expect(IllegalStateException.class);

        // when
        // then
        reader.next();
    }

    @Test
    public void shouldNotHaveNext()
    {
        // given
        reader.wrap(logStream);

        // when
        // then
        assertThat(reader.hasNext()).isFalse();
    }

    @Test
    public void shouldHaveNext()
    {
        // given
        final long position = writeEvent(1, EVENT_VALUE);
        reader.wrap(logStream);

        // when
        // then
        TestUtil.waitUntil(() -> reader.hasNext());

        final LoggedEvent next = reader.next();
        assertThat(next.getKey()).isEqualTo(1);
        assertThat(next.getPosition()).isEqualTo(position);
        assertThat(reader.hasNext()).isFalse();
    }


    @Test
    public void shouldThrowNoSuchElementExceptionOnNextCall()
    {
        //given
        reader.wrap(logStream);

        // expect
        expectedException.expectMessage("Api protocol violation: No next log entry available; You need to probe with hasNext() first.");
        expectedException.expect(NoSuchElementException.class);

        // when
        // then
        reader.next();
    }

    @Test
    public void shouldReturnPositionOfCurrentLoggedEvent()
    {
        // given
        final long position = writeEvent(0xFF, EVENT_VALUE);
        reader.wrap(logStream);

        // when
        TestUtil.waitUntil(() -> reader.hasNext());

        // then
        assertThat(reader.getPosition()).isEqualTo(position);
    }

    @Test
    public void shouldReturnNoPositionIfNotActiveOrInitialized()
    {
        // given
        writeEvent(0xFF, EVENT_VALUE);

        // then
        assertThat(reader.getPosition()).isEqualTo(-1);
    }

    @Test
    public void shouldThrowIteratorNotInitializedIfReaderWasClosedAndHasNextIsCalled()
    {
        // given
        reader.wrap(logStream);
        reader.close();
        final long position = writeEvent(0xFF, EVENT_VALUE);
        TestUtil.waitUntil(() -> logStream.getCurrentAppenderPosition() > position);

        //expect
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("Iterator not initialized");

        // when
        reader.hasNext();
    }

    @Test
    public void shouldReopenAndReturnLoggedEvent()
    {
        // given
        reader.close();
        final long position = writeEvent(0xFF, EVENT_VALUE);
        reader.wrap(logStream);

        // when
        TestUtil.waitUntil(() -> reader.hasNext());

        // then
        final LoggedEvent loggedEvent = reader.next();
        assertThat(loggedEvent.getKey()).isEqualTo(0xFF);
        assertThat(loggedEvent.getPosition()).isEqualTo(position);
    }

    @Test
    public void shouldReturnUncommitedLoggedEvent()
    {
        // given
        final BufferedLogStreamReader reader = new BufferedLogStreamReader(true);

        logStream.setCommitPosition(Long.MIN_VALUE);
        final long position = writeEvent(0xFF, EVENT_VALUE);
        reader.wrap(logStream);

        // when
        TestUtil.waitUntil(() -> reader.hasNext());

        // then
        final LoggedEvent loggedEvent = reader.next();
        assertThat(loggedEvent.getKey()).isEqualTo(0xFF);
        assertThat(loggedEvent.getPosition()).isEqualTo(position);

        reader.close();
    }

    @Test
    public void shouldNotReturnUncommitedLoggedEvent()
    {
        // given
        final long firstPos = writeEvent(1, EVENT_VALUE);
        logStream.setCommitPosition(firstPos);
        final long secondPos = writeEvent(2, EVENT_VALUE);
        reader.wrap(logStream);

        // when
        TestUtil.waitUntil(() -> logStream.getLogStreamController().getCurrentAppenderPosition() > secondPos);

        // then
        final LoggedEvent loggedEvent = reader.next();
        assertThat(loggedEvent.getKey()).isEqualTo(1);
        assertThat(loggedEvent.getPosition()).isEqualTo(firstPos);

        assertThat(reader.hasNext()).isFalse();
    }

    @Test
    public void shouldNotReturnLoggedEventUntilCommited()
    {
        // given
        final long position = writeEvent(0xFF, EVENT_VALUE);
        logStream.setCommitPosition(Long.MIN_VALUE);
        reader.wrap(logStream);

        // when
        TestUtil.waitUntil(() -> logStream.getLogStreamController().getCurrentAppenderPosition() > position);

        // then
        assertThat(reader.hasNext()).isFalse();

        // when
        logStream.setCommitPosition(position);

        // then
        assertThat(reader.hasNext()).isTrue();
        final LoggedEvent loggedEvent = reader.next();
        assertThat(loggedEvent.getKey()).isEqualTo(0xFF);
        assertThat(loggedEvent.getPosition()).isEqualTo(position);
    }

    @Test
    public void shouldNotSeekToUncommitedLoggedEvent()
    {
        // given
        final long firstPos = writeEvent(1, EVENT_VALUE);
        logStream.setCommitPosition(firstPos);
        final long secondPos = writeEvent(2, EVENT_VALUE);
        reader.wrap(logStream);

        // when
        TestUtil.waitUntil(() -> reader.hasNext());
        reader.seek(secondPos);

        // then
        assertThat(reader.hasNext()).isFalse();
    }

    @Test
    public void shouldSeekToUncommitedLoggedEventIfFlagIsSet()
    {
        // given
        final BufferedLogStreamReader reader = new BufferedLogStreamReader(true);
        final long firstPos = writeEvent(1, EVENT_VALUE);
        logStream.setCommitPosition(firstPos);
        final long secondPos = writeEvent(2, EVENT_VALUE);
        reader.wrap(logStream);

        // when
        TestUtil.waitUntil(() -> reader.hasNext());
        reader.seek(secondPos);

        // then
        assertThat(reader.hasNext()).isTrue();
        final LoggedEvent loggedEvent = reader.next();
        assertThat(loggedEvent.getKey()).isEqualTo(2);
        assertThat(loggedEvent.getPosition()).isEqualTo(secondPos);
    }

    @Test
    public void shouldWrapAndSeekToEvent()
    {
        // given
        writeEvent(1, EVENT_VALUE);
        final long secondPos = writeEvent(2, EVENT_VALUE);

        TestUtil.waitUntil(() -> logStream.getLogStreamController().getCurrentAppenderPosition() > secondPos);

        // when
        reader.wrap(logStream, secondPos);

        // then
        final LoggedEvent loggedEvent = reader.next();
        assertThat(loggedEvent.getKey()).isEqualTo(2);
        assertThat(loggedEvent.getPosition()).isEqualTo(secondPos);

        assertThat(reader.hasNext()).isFalse();
    }

    @Test
    public void shouldReturnLastEventAfterSeekToLastEvent()
    {
        // given
        final long positions[] = writeEvents(10, EVENT_VALUE);
        reader.wrap(logStream);

        TestUtil.waitUntil(() -> logStream.getLogStreamController().getCurrentAppenderPosition() > positions[9]);

        // when
        reader.seekToLastEvent();

        // then
        assertThat(reader.hasNext()).isTrue();
        final LoggedEvent loggedEvent = reader.next();
        assertThat(loggedEvent.getKey()).isEqualTo(9);
        assertThat(loggedEvent.getPosition()).isEqualTo(positions[9]);

        assertThat(reader.hasNext()).isFalse();
    }

    @Test
    public void shouldIncreaseBufferAndSeekToLastEventIfSmallAndBigDoesNotFitTogether()
    {
        // given
        final byte[] bytes = new byte[1024 - 56];
        final long[] positions = writeEvents(31,  new UnsafeBuffer(bytes));
        TestUtil.waitUntil(() -> logStream.getCurrentAppenderPosition() > positions[30]);

        // when
        reader.wrap(logStream);
        final byte[] bigEventValue = new byte[BufferedLogStreamReader.DEFAULT_INITIAL_BUFFER_CAPACITY - 56 - 1];
        final long[] bigEventPositions = writeEvents(3, new UnsafeBuffer(bigEventValue));
        TestUtil.waitUntil(() -> logStream.getCurrentAppenderPosition() > bigEventPositions[2]);

        // then
        assertThat(reader.seek(bigEventPositions[2])).isTrue();
        final LoggedEvent bigEvent = reader.next();
        assertThat(bigEvent.getPosition()).isEqualTo(bigEventPositions[2]);
        assertThat(bigEvent.getKey()).isEqualTo(2);
        assertThat(reader.hasNext()).isFalse();
    }

    @Test
    public void shouldResizeBufferAndIterateOverSmallAndBigLoggedEvent()
    {
        // given
        final long[] positions = writeEvents(100, EVENT_VALUE);
        TestUtil.waitUntil(() -> logStream.getCurrentAppenderPosition() > positions[99]);

        // when
        reader.wrap(logStream);

        // then
        for (int i = 0; i < 99; i++)
        {
            final LoggedEvent loggedEvent = reader.next();
            assertThat(loggedEvent.getKey()).isEqualTo(i);
            assertThat(loggedEvent.getPosition()).isEqualTo(positions[i]);
        }
        assertThat(reader.hasNext()).isTrue();

        // when
        final byte[] bytes = new byte[BufferedLogStreamReader.DEFAULT_INITIAL_BUFFER_CAPACITY * 2];
        final long bigEventPosition = writeEvent(0xFF, new UnsafeBuffer(bytes));
        TestUtil.waitUntil(() -> logStream.getCurrentAppenderPosition() > bigEventPosition);

        // then
        LoggedEvent loggedEvent = reader.next();
        assertThat(loggedEvent.getKey()).isEqualTo(99);
        assertThat(loggedEvent.getPosition()).isEqualTo(positions[99]);
        assertThat(reader.hasNext()).isTrue();

        loggedEvent = reader.next();
        assertThat(loggedEvent.getKey()).isEqualTo(0xFF);
        assertThat(loggedEvent.getPosition()).isEqualTo(bigEventPosition);
        assertThat(reader.hasNext()).isFalse();
    }

    @Test
    public void shouldReturnBigLoggedEvent()
    {
        // given
        final byte[] bytes = new byte[BufferedLogStreamReader.DEFAULT_INITIAL_BUFFER_CAPACITY * 2];
        final long position = writeEvent(0xFF, new UnsafeBuffer(bytes));
        reader.wrap(logStream);

        // when
        TestUtil.waitUntil(() -> reader.hasNext());

        // then
        final LoggedEvent loggedEvent = reader.next();
        assertThat(loggedEvent.getKey()).isEqualTo(0xFF);
        assertThat(loggedEvent.getPosition()).isEqualTo(position);
        assertThat(reader.hasNext()).isFalse();
    }

    @Test
    public void shouldSeekToLastBigLoggedEvents()
    {
        // given
        final byte[] bytes = new byte[BufferedLogStreamReader.DEFAULT_INITIAL_BUFFER_CAPACITY * 2];
        final long[] positions = writeEvents(1000, new UnsafeBuffer(bytes));
        reader.wrap(logStream);

        // when
        TestUtil.waitUntil(() -> logStream.getCurrentAppenderPosition() > positions[999]);
        reader.seekToLastEvent();

        // then
        final LoggedEvent loggedEvent = reader.next();
        assertThat(loggedEvent.getKey()).isEqualTo(999);
        assertThat(loggedEvent.getPosition()).isEqualTo(positions[999]);

        assertThat(reader.hasNext()).isFalse();
    }

    @Test
    public void shouldReturnBigLoggedEvents()
    {
        // given
        final byte[] bytes = new byte[BufferedLogStreamReader.DEFAULT_INITIAL_BUFFER_CAPACITY * 2];
        final long[] positions = writeEvents(1000, new UnsafeBuffer(bytes));
        reader.wrap(logStream);

        // when
        TestUtil.waitUntil(() -> logStream.getLogStreamController().getCurrentAppenderPosition() > positions[999]);

        // then
        int idx = 0;
        while (reader.hasNext())
        {
            final LoggedEvent loggedEvent = reader.next();
            assertThat(loggedEvent.getKey()).isEqualTo(idx);
            assertThat(loggedEvent.getPosition()).isEqualTo(positions[idx]);
            idx++;
        }
        assertThat(idx).isEqualTo(1000);
        assertThat(reader.hasNext()).isFalse();
    }

    @Test
    public void shouldIterateOverManyEvents()
    {
        // given
        final long[] positions = writeEvents(WORK_COUNT, EVENT_VALUE);
        reader.wrap(logStream);

        // when
        TestUtil.waitUntil(() -> logStream.getLogStreamController().getCurrentAppenderPosition() > positions[WORK_COUNT - 1]);

        // then
        int idx = 0;
        while (reader.hasNext())
        {
            final LoggedEvent loggedEvent = reader.next();
            assertThat(loggedEvent.getKey()).isEqualTo(idx);
            assertThat(loggedEvent.getPosition()).isEqualTo(positions[idx]);
            idx++;
        }
        assertThat(idx).isEqualTo(WORK_COUNT);
    }

}

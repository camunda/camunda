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

import static io.zeebe.util.StringUtil.getBytes;
import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 */
public class LogStreamReaderTest
{
    private static final UnsafeBuffer EVENT_VALUE = new UnsafeBuffer(getBytes("test"));

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
    }

    private long[] writeEvents(int count)
    {
        final long[] positions = new long[count];

        for (int i = 0; i < count; i++)
        {
            positions[i] = writeEvent(i, EVENT_VALUE);
        }
        return positions;
    }

    private long writeEvent(long key, DirectBuffer eventValue)
    {
        final long position = writer
            .key(key)
            .value(eventValue)
            .tryWrite();

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
    // TODO fails?
    public void shouldNotHaveNext()
    {
        // given
        reader.wrap(logStream);

        // when
        // then
        assertThat(reader.hasNext()).isFalse();
    }

    @Test
    public void shouldReturnTrueOnHaveNextIfSeekIsCalledBefore()
    {
        // given
        writeEvent(1, EVENT_VALUE);
        reader.wrap(logStream);
        logStream.getLogStreamController().doWork();

        // when
        // then
        assertThat(reader.hasNext()).isTrue();
    }

    @Test
    public void shouldReturnLoggedEvent()
    {
        // given
        writeEvent(0xFF, EVENT_VALUE);
        reader.wrap(logStream);
        logStream.getLogStreamController().doWork();

        // when
        final LoggedEvent loggedEvent = reader.next();

        // then
        assertThat(loggedEvent.getKey()).isEqualTo(0xFF);
    }


    @Test
    public void shouldReturnBigLoggedEvent()
    {
        // given
        final byte[] bytes = new byte[1024 * 64];
        writeEvent(0xFF, new UnsafeBuffer(bytes));
        reader.wrap(logStream);
        logStream.getLogStreamController().doWork();

        // when
        final LoggedEvent loggedEvent = reader.next();

        // then
        assertThat(loggedEvent.getKey()).isEqualTo(0xFF);
    }

}

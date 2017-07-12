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

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.*;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.positionOffset;
import static io.zeebe.logstreams.log.MockLogStorage.newLogEntries;
import static io.zeebe.logstreams.log.MockLogStorage.newLogEntry;
import static io.zeebe.util.StringUtil.getBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.nio.ByteBuffer;
import java.util.NoSuchElementException;

import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.buffer.DirectBufferReader;
import org.agrona.*;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class BufferedLogStreamReaderTest
{
    @Mock
    private LogBlockIndex mockBlockIndex;

    @Mock
    private LogStream mockLogStream;

    private MockLogStorage mockLogStorage;

    private BufferedLogStreamReader reader;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void init()
    {
        MockitoAnnotations.initMocks(this);

        mockLogStorage = new MockLogStorage();

        when(mockLogStream.getLogStorage()).thenReturn(mockLogStorage.getMock());
        when(mockLogStream.getLogBlockIndex()).thenReturn(mockBlockIndex);

        reader = new BufferedLogStreamReader();
    }

    @After
    public void cleanUp()
    {
        reader.close();
    }

    @Test
    public void shouldReadEvent()
    {
        when(mockBlockIndex.size()).thenReturn(1);
        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);
        when(mockBlockIndex.lookupBlockAddress(1L)).thenReturn(10L);

        mockLogStorage.add(newLogEntry()
                .address(10)
                .position(1)
                .key(2)
                .sourceEventLogStreamId(3)
                .sourceEventPosition(4L)
                .producerId(5)
                .value(getBytes("event")));

        when(mockLogStream.getCommitPosition()).thenReturn(1L);

        reader.wrap(mockLogStream);

        final boolean hasNext = reader.hasNext();
        assertThat(hasNext).isTrue();

        final LoggedEvent event = reader.next();

        assertThat(event).isNotNull();
        assertThat(event.getPosition()).isEqualTo(1L);
        assertThat(event.getKey()).isEqualTo(2L);

        assertThat(event.getSourceEventLogStreamPartitionId()).isEqualTo(3);
        assertThat(event.getSourceEventPosition()).isEqualTo(4L);

        assertThat(event.getProducerId()).isEqualTo(5);

        final DirectBufferReader readBuffer = new DirectBufferReader();
        event.readValue(readBuffer);

        assertThat(readBuffer.byteArray()).isEqualTo(getBytes("event"));
    }

    @Test
    public void shouldReadEventValueBuffer()
    {
        when(mockBlockIndex.size()).thenReturn(1);
        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);
        when(mockBlockIndex.lookupBlockAddress(1L)).thenReturn(10L);

        mockLogStorage.add(newLogEntry().address(10).value(getBytes("event")));

        reader.wrap(mockLogStream);

        final boolean hasNext = reader.hasNext();
        assertThat(hasNext).isTrue();

        final LoggedEvent event = reader.next();

        final DirectBuffer valueBuffer = event.getValueBuffer();
        final byte[] readValueBuffer = new byte[event.getValueLength()];
        valueBuffer.getBytes(event.getValueOffset(), readValueBuffer);

        assertThat(readValueBuffer).isEqualTo(getBytes("event"));
    }

    @Test
    public void shouldReadEventMetadataBuffer()
    {
        when(mockBlockIndex.size()).thenReturn(1);
        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);
        when(mockBlockIndex.lookupBlockAddress(1L)).thenReturn(10L);

        mockLogStorage.add(newLogEntry().address(10).metadata(getBytes("metadata")).value(getBytes("event")));

        reader.wrap(mockLogStream);

        final boolean hasNext = reader.hasNext();
        assertThat(hasNext).isTrue();

        final LoggedEvent event = reader.next();

        final DirectBuffer metadataBuffer = event.getMetadata();
        final byte[] readMetadataBuffer = new byte[event.getMetadataLength()];
        metadataBuffer.getBytes(event.getMetadataOffset(), readMetadataBuffer);

        assertThat(readMetadataBuffer).isEqualTo(getBytes("metadata"));

        // AND: value can also be read

        final DirectBuffer valueBuffer = event.getValueBuffer();
        final byte[] readValueBuffer = new byte[event.getValueLength()];
        valueBuffer.getBytes(event.getValueOffset(), readValueBuffer);

        assertThat(readValueBuffer).isEqualTo(getBytes("event"));
    }

    @Test
    public void shouldReadFirstEvent()
    {
        when(mockBlockIndex.size()).thenReturn(2);

        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);
        when(mockBlockIndex.getLogPosition(1)).thenReturn(5L);

        when(mockBlockIndex.lookupBlockAddress(1L)).thenReturn(10L);
        when(mockBlockIndex.lookupBlockAddress(5L)).thenReturn(15L);

        mockLogStorage
            .add(newLogEntries(4).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(7L);

        reader.wrap(mockLogStream);

        reader.hasNext();
        final LoggedEvent event = reader.next();

        assertThat(event.getPosition()).isEqualTo(1L);
    }

    @Test
    public void shouldNotReadUncommittedFirstEvent()
    {
        when(mockBlockIndex.size()).thenReturn(2);

        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);
        when(mockBlockIndex.getLogPosition(1)).thenReturn(5L);

        when(mockBlockIndex.lookupBlockAddress(1L)).thenReturn(10L);
        when(mockBlockIndex.lookupBlockAddress(5L)).thenReturn(15L);

        mockLogStorage
            .add(newLogEntries(4).address(10).position(2).nextAddress(15))
            .add(newLogEntries(3).address(15).position(6));

        when(mockLogStream.getCommitPosition()).thenReturn(1L);

        reader.wrap(mockLogStream);

        assertThat(reader.hasNext()).isFalse();
    }

    @Test
    public void shouldReadFirstEventAfterCommit()
    {
        when(mockBlockIndex.size()).thenReturn(2);

        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);
        when(mockBlockIndex.getLogPosition(1)).thenReturn(5L);

        when(mockBlockIndex.lookupBlockAddress(1L)).thenReturn(10L);
        when(mockBlockIndex.lookupBlockAddress(5L)).thenReturn(15L);

        mockLogStorage
            .add(newLogEntries(4).address(10).position(2).nextAddress(15))
            .add(newLogEntries(3).address(15).position(6));

        when(mockLogStream.getCommitPosition()).thenReturn(1L);

        reader.wrap(mockLogStream);

        assertThat(reader.hasNext()).isFalse();

        when(mockLogStream.getCommitPosition()).thenReturn(2L);

        assertThat(reader.hasNext()).isTrue();

        final LoggedEvent event = reader.next();
        assertThat(event.getPosition()).isEqualTo(2L);
    }

    @Test
    public void shouldReadEventAtPosition()
    {
        when(mockBlockIndex.size()).thenReturn(2);

        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);
        when(mockBlockIndex.getLogPosition(1)).thenReturn(5L);

        when(mockBlockIndex.lookupBlockAddress(1L)).thenReturn(10L);
        when(mockBlockIndex.lookupBlockAddress(3L)).thenReturn(10L);
        when(mockBlockIndex.lookupBlockAddress(5L)).thenReturn(15L);

        mockLogStorage
            .add(newLogEntries(4).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(7L);

        reader.wrap(mockLogStream, 3);

        reader.hasNext();
        final LoggedEvent event = reader.next();

        assertThat(event.getPosition()).isEqualTo(3L);
    }

    @Test
    public void shouldNotReadEventAtUncommittedPosition()
    {
        when(mockBlockIndex.size()).thenReturn(1);

        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);

        when(mockBlockIndex.lookupBlockAddress(5L)).thenReturn(10L);

        mockLogStorage
            .add(newLogEntries(4).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(4L);

        reader.wrap(mockLogStream, 5L);

        assertThat(reader.hasNext()).isFalse();
    }

    @Test
    public void shouldReadEventAtCommittedPosition()
    {
        when(mockBlockIndex.size()).thenReturn(1);

        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);

        when(mockBlockIndex.lookupBlockAddress(5L)).thenReturn(10L);

        mockLogStorage
            .add(newLogEntries(4).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(4L);

        reader.wrap(mockLogStream, 5L);

        assertThat(reader.hasNext()).isFalse();

        when(mockLogStream.getCommitPosition()).thenReturn(5L);

        reader.wrap(mockLogStream, 5L);

        assertThat(reader.hasNext()).isTrue();

        final LoggedEvent event = reader.next();
        assertThat(event.getPosition()).isEqualTo(5L);
    }

    @Test
    public void shouldIterateOverTheLog()
    {
        when(mockBlockIndex.size()).thenReturn(2);

        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);
        when(mockBlockIndex.getLogPosition(1)).thenReturn(5L);

        when(mockBlockIndex.lookupBlockAddress(1L)).thenReturn(10L);
        when(mockBlockIndex.lookupBlockAddress(5L)).thenReturn(15L);

        mockLogStorage
            .add(newLogEntries(4).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(7L);

        reader.wrap(mockLogStream);

        long position = 1;
        while (reader.hasNext())
        {
            final LoggedEvent event = reader.next();
            assertThat(event.getPosition()).isEqualTo(position);
            position += 1;
        }
        assertThat(position).isEqualTo(8);
    }

    @Test
    public void shouldIterateOverCommittedEntries()
    {
        when(mockBlockIndex.size()).thenReturn(2);

        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);
        when(mockBlockIndex.getLogPosition(1)).thenReturn(5L);

        when(mockBlockIndex.lookupBlockAddress(1L)).thenReturn(10L);
        when(mockBlockIndex.lookupBlockAddress(5L)).thenReturn(15L);

        mockLogStorage
            .add(newLogEntries(4).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(1L);

        reader.wrap(mockLogStream);

        long position = 1;
        while (reader.hasNext())
        {
            final LoggedEvent event = reader.next();

            assertThat(event.getPosition()).isEqualTo(position);
            assertThat(reader.hasNext()).isFalse();

            position += 1;
            when(mockLogStream.getCommitPosition()).thenReturn(position);
        }

        assertThat(position).isEqualTo(8);
    }

    @Test
    public void shouldSeekToFirstEvent()
    {
        when(mockBlockIndex.size()).thenReturn(2);

        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);
        when(mockBlockIndex.getLogPosition(1)).thenReturn(5L);

        when(mockBlockIndex.lookupBlockAddress(1L)).thenReturn(10L);
        when(mockBlockIndex.lookupBlockAddress(5L)).thenReturn(15L);

        mockLogStorage
            .add(newLogEntries(4).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(7L);

        reader.wrap(mockLogStream, 5);

        reader.seekToFirstEvent();

        reader.hasNext();
        final LoggedEvent event = reader.next();

        assertThat(event.getPosition()).isEqualTo(1L);
        assertThat(reader.getPosition()).isEqualTo(1L);
    }

    @Test
    public void shouldNotSeekToFirstUnCommittedEvent()
    {
        when(mockBlockIndex.size()).thenReturn(2);

        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);
        when(mockBlockIndex.getLogPosition(1)).thenReturn(5L);

        when(mockBlockIndex.lookupBlockAddress(1L)).thenReturn(10L);
        when(mockBlockIndex.lookupBlockAddress(5L)).thenReturn(15L);

        mockLogStorage
            .add(newLogEntries(4).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(-1L);

        reader.wrap(mockLogStream, 5);

        reader.seekToFirstEvent();

        assertThat(reader.hasNext()).isFalse();
    }

    @Test
    public void shouldSeekToLastEvent()
    {
        when(mockBlockIndex.lookupBlockAddress(7L)).thenReturn(15L);

        mockLogStorage
            .add(newLogEntries(4).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(7L);

        reader.wrap(mockLogStream);

        reader.seekToLastEvent();

        reader.hasNext();
        final LoggedEvent event = reader.next();

        assertThat(event.getPosition()).isEqualTo(7L);
        assertThat(reader.getPosition()).isEqualTo(7L);
    }

    @Test
    public void shouldNotSeekToLastEvent()
    {
        when(mockBlockIndex.lookupBlockAddress(7L)).thenReturn(15L);

        mockLogStorage
            .add(newLogEntries(4).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(-1L);

        reader.wrap(mockLogStream);

        reader.seekToLastEvent();

        assertThat(reader.hasNext()).isFalse();
    }

    @Test
    public void shouldSeekToLastCommittedEvent()
    {
        when(mockBlockIndex.lookupBlockAddress(5L)).thenReturn(10L);

        mockLogStorage
            .add(newLogEntries(4).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(5L);

        reader.wrap(mockLogStream);

        reader.seekToLastEvent();

        assertThat(reader.hasNext()).isTrue();

        final LoggedEvent event = reader.next();
        assertThat(event.getPosition()).isEqualTo(5L);
    }

    @Test
    public void shouldSeekToPosition()
    {
        when(mockBlockIndex.size()).thenReturn(2);

        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);
        when(mockBlockIndex.getLogPosition(1)).thenReturn(5L);

        when(mockBlockIndex.lookupBlockAddress(1L)).thenReturn(10L);
        when(mockBlockIndex.lookupBlockAddress(3L)).thenReturn(10L);
        when(mockBlockIndex.lookupBlockAddress(5L)).thenReturn(15L);

        mockLogStorage
            .add(newLogEntries(4).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(7L);

        reader.wrap(mockLogStream);

        final boolean found = reader.seek(3);

        reader.hasNext();
        final LoggedEvent event = reader.next();

        assertThat(found).isTrue();
        assertThat(event.getPosition()).isEqualTo(3L);
        assertThat(reader.getPosition()).isEqualTo(3L);
    }

    @Test
    public void shouldSeekToCommittedPosition()
    {
        when(mockBlockIndex.size()).thenReturn(1);

        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);

        when(mockBlockIndex.lookupBlockAddress(7L)).thenReturn(10L);

        mockLogStorage
            .add(newLogEntries(4).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(4L);

        reader.wrap(mockLogStream);

        final boolean found = reader.seek(7);
        assertThat(found).isFalse();

        assertThat(reader.hasNext()).isFalse();
        assertThat(reader.getPosition()).isEqualTo(4L);
    }

    @Test
    public void shouldSeekToNextPositionIfNotExist()
    {
        when(mockBlockIndex.size()).thenReturn(2);

        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);
        when(mockBlockIndex.getLogPosition(1)).thenReturn(5L);

        when(mockBlockIndex.lookupBlockAddress(1L)).thenReturn(10L);
        when(mockBlockIndex.lookupBlockAddress(3L)).thenReturn(10L);
        when(mockBlockIndex.lookupBlockAddress(5L)).thenReturn(15L);

        mockLogStorage
            .add(newLogEntries(2).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(7L);

        reader.wrap(mockLogStream);

        final boolean found = reader.seek(3);

        reader.hasNext();
        final LoggedEvent event = reader.next();

        assertThat(found).isFalse();
        assertThat(event.getPosition()).isEqualTo(5L);
        assertThat(reader.getPosition()).isEqualTo(5L);
    }

    @Test
    public void shouldSeekToFirstEventIfIndexNotExist()
    {
        when(mockBlockIndex.size()).thenReturn(0);
        when(mockBlockIndex.lookupBlockAddress(anyLong())).thenReturn(-1L);

        mockLogStorage
            .firstBlockAddress(10)
            .add(newLogEntries(4).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(7L);

        reader.wrap(mockLogStream);

        reader.seekToFirstEvent();

        final boolean hasNext = reader.hasNext();
        assertThat(hasNext).isTrue();

        final LoggedEvent event = reader.next();

        assertThat(event.getPosition()).isEqualTo(1L);
        assertThat(reader.getPosition()).isEqualTo(1L);
    }

    @Test
    public void shouldSeekToLastEventIfIndexNotExist()
    {
        when(mockBlockIndex.size()).thenReturn(0);
        when(mockBlockIndex.lookupBlockAddress(anyLong())).thenReturn(-1L);

        mockLogStorage
            .firstBlockAddress(10)
            .add(newLogEntries(4).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(7L);

        reader.wrap(mockLogStream);

        reader.seekToLastEvent();

        final boolean hasNext = reader.hasNext();
        assertThat(hasNext).isTrue();

        final LoggedEvent event = reader.next();

        assertThat(event.getPosition()).isEqualTo(7L);
        assertThat(reader.getPosition()).isEqualTo(7L);
    }

    @Test
    public void shouldSeekToPositionIfIndexNotExist()
    {
        when(mockBlockIndex.size()).thenReturn(0);
        when(mockBlockIndex.lookupBlockAddress(anyLong())).thenReturn(-1L);

        mockLogStorage
            .firstBlockAddress(10)
            .add(newLogEntries(4).address(10).position(1).nextAddress(15))
            .add(newLogEntries(3).address(15).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(7L);

        reader.wrap(mockLogStream);

        reader.seek(3);

        final boolean hasNext = reader.hasNext();
        assertThat(hasNext).isTrue();

        final LoggedEvent event = reader.next();

        assertThat(event.getPosition()).isEqualTo(3L);
        assertThat(reader.getPosition()).isEqualTo(3L);
    }

    @Test
    public void shouldSeekForEventWhichDoesNotFitCompletelyIntoBuffer()
    {
        final LogStorage logStorage = mock(LogStorage.class);
        when(logStorage.getFirstBlockAddress()).thenReturn(4096L);
        when(logStorage.read(any(ByteBuffer.class), anyLong())).thenReturn(-2L);
        when(logStorage.read(any(ByteBuffer.class), eq(4096L))).thenAnswer(
            withEvents(164, 4, 4608));

        when(logStorage.read(any(ByteBuffer.class), eq(4608L))).thenAnswer(
            (InvocationOnMock invocationMock) ->
            {
                final ByteBuffer byteBuffer = (ByteBuffer) invocationMock.getArguments()[0];
                final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);
                buffer.wrap(byteBuffer);

                fillBuffer(buffer, byteBuffer.position(), byteBuffer.capacity());

                byteBuffer.position(512);
                byteBuffer.limit(512);
                return 4608;
            });

        final LogBlockIndex logBlockIndex = mock(LogBlockIndex.class);
        when(logBlockIndex.lookupBlockAddress(anyLong())).thenReturn(-1L);

        final LogStream logStream = mock(LogStream.class);
        when(logStream.getLogStorage()).thenReturn(logStorage);
        when(logStream.getLogBlockIndex()).thenReturn(logBlockIndex);
        when(logStream.getCommitPosition()).thenReturn(5L);

        // given
        try (BufferedLogStreamReader logStreamReader = new BufferedLogStreamReader(512))
        {
            logStreamReader.wrap(logStream);

            // when
            final boolean found = logStreamReader.seek(3);

            // then
            assertThat(found).isTrue();
        }
    }

    @Test
    public void shouldNotHasEventIfPositionNotExist()
    {
        when(mockBlockIndex.size()).thenReturn(1);

        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);

        when(mockBlockIndex.lookupBlockAddress(1L)).thenReturn(10L);
        when(mockBlockIndex.lookupBlockAddress(3L)).thenReturn(10L);

        mockLogStorage.add(newLogEntries(2).address(10).position(1));

        reader.wrap(mockLogStream);

        final boolean found = reader.seek(3);

        final boolean hasNext = reader.hasNext();

        assertThat(found).isFalse();
        assertThat(hasNext).isFalse();
    }

    @Test
    public void shouldBufferEvents()
    {
        when(mockBlockIndex.size()).thenReturn(1);
        when(mockBlockIndex.getLogPosition(0)).thenReturn(1L);
        when(mockBlockIndex.lookupBlockAddress(1L)).thenReturn(10L);

        mockLogStorage.add(newLogEntries(4).address(10).position(1));

        reader.wrap(mockLogStream);

        // iterate over the log
        while (reader.hasNext())
        {
            reader.next();
        }

        // assert that the reader copies larger blocks from the log into the buffer to batch I/O
        verify(mockLogStorage.getMock(), times(1)).read(any(), eq(10L));
    }

    @Test
    public void shouldNotHasEventIfNotInitialized()
    {
        thrown.expect(IllegalStateException.class);

        reader.hasNext();
    }

    @Test
    public void shouldNotHasEventIfLogIsEmpty()
    {
        when(mockBlockIndex.lookupBlockAddress(anyLong())).thenReturn(-1L);
        mockLogStorage.firstBlockAddress(-1);

        reader.wrap(mockLogStream);

        final boolean hasNext = reader.hasNext();

        assertThat(hasNext).isFalse();
    }

    @Test
    public void shouldNotReadEventIfLogIsEmpty()
    {
        when(mockBlockIndex.lookupBlockAddress(anyLong())).thenReturn(-1L);
        mockLogStorage.firstBlockAddress(-1);

        reader.wrap(mockLogStream);

        reader.hasNext();

        thrown.expect(NoSuchElementException.class);

        reader.next();
    }

    @Test
    public void shouldNotGetPositionIfNotInitialized()
    {
        thrown.expect(IllegalStateException.class);

        reader.getPosition();
    }

    @Test
    public void shouldNotGetPositionIfLogIsEmpty()
    {
        when(mockBlockIndex.lookupBlockAddress(anyLong())).thenReturn(-1L);
        mockLogStorage.firstBlockAddress(-1);

        reader.wrap(mockLogStream);

        thrown.expect(NoSuchElementException.class);

        reader.getPosition();
    }

    @Test
    public void shouldNotGetPositionIfEntryIsNotCommitted()
    {
        when(mockBlockIndex.lookupBlockAddress(anyLong())).thenReturn(10L);

        mockLogStorage.add(newLogEntries(4).address(10).position(5));

        when(mockLogStream.getCommitPosition()).thenReturn(4L);

        reader.wrap(mockLogStream);

        thrown.expect(NoSuchElementException.class);

        reader.getPosition();
    }

    @Test
    public void shouldReadFirstEventWhenPositionIsNotContainedInBlockIndex()
    {
        when(mockBlockIndex.size()).thenReturn(1);
        when(mockBlockIndex.lookupBlockAddress(5L)).thenReturn(-1L);

        mockLogStorage
            .firstBlockAddress(100)
            .add(newLogEntry()
                .address(100)
                .position(10)
                .key(2)
                .sourceEventLogStreamId(3)
                .sourceEventPosition(4L)
                .producerId(5)
                .value(getBytes("event")));

        when(mockLogStream.getCommitPosition()).thenReturn(10L);

        reader.wrap(mockLogStream);

        reader.seek(5L);

        assertThat(reader.hasNext()).isTrue();

        final LoggedEvent event = reader.next();

        assertThat(event).isNotNull();
        assertThat(event.getPosition()).isEqualTo(10L);
    }

    private void fillBuffer(MutableDirectBuffer buffer, int startIdex, int endIdx)
    {
        for (int offset = startIdex; offset < endIdx; offset += BitUtil.SIZE_OF_INT)
        {
            buffer.putInt(offset, Integer.MAX_VALUE);
        }
    }

    private Answer<?> withEvents(int eventLength, int eventCount, long resultAddress)
    {
        return (InvocationOnMock invocationMock) ->
        {
            final ByteBuffer byteBuffer = (ByteBuffer) invocationMock.getArguments()[0];
            final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);
            buffer.wrap(byteBuffer);

            int offset = byteBuffer.position();
            for (int event = 0; event < eventCount && offset < byteBuffer.capacity(); event++)
            {
                // first event
                buffer.putInt(lengthOffset(offset), eventLength);
                buffer.putLong(positionOffset(messageOffset(offset)), event + 1);
                fillBuffer(buffer, positionOffset(messageOffset(offset)) + 8, alignedLength(eventLength));
                offset += alignedLength(eventLength);
            }

            byteBuffer.position(byteBuffer.capacity());
            byteBuffer.limit(byteBuffer.capacity());

            return resultAddress;
        };
    }
}

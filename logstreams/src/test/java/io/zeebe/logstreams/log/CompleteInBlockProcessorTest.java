/**
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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.logstreams.impl.CompleteEventsInBlockProcessor;
import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.logstreams.impl.log.fs.FsLogStorageConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.*;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.*;
import static io.zeebe.logstreams.spi.LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;

/**
 * @author Christopher Zell <christopher.zell@camunda.com>
 */
public class CompleteInBlockProcessorTest
{
    private static final int SEGMENT_SIZE = 1024 * 16;

    protected static final int LENGTH = headerLength(0, 0);
    protected static final int ALIGNED_LEN = alignedLength(LENGTH);

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private CompleteEventsInBlockProcessor processor;
    private String logPath;
    private FsLogStorageConfiguration fsStorageConfig;
    private FsLogStorage fsLogStorage;
    private long appendedAddress;

    @Before
    public void init()
    {
        processor = new CompleteEventsInBlockProcessor();
        logPath = tempFolder.getRoot().getAbsolutePath();
        fsStorageConfig = new FsLogStorageConfiguration(SEGMENT_SIZE, logPath, 0, false);
        fsLogStorage = new FsLogStorage(fsStorageConfig);

        final ByteBuffer writeBuffer = ByteBuffer.allocate(192);
        final MutableDirectBuffer directBuffer = new UnsafeBuffer(0, 0);
        directBuffer.wrap(writeBuffer);

        /*
         Buffer: [4test4asdf30012345678901234567890123456789]
         */
        //small events
        int idx = 0;
        directBuffer.putInt(lengthOffset(idx), LENGTH);
        directBuffer.putLong(positionOffset(messageOffset(idx)), 1);

        idx = ALIGNED_LEN;
        directBuffer.putInt(idx, LENGTH);
        directBuffer.putLong(positionOffset(messageOffset(idx)), 2);

        // a large event
        idx = 2 * ALIGNED_LEN;
        directBuffer.putInt(idx, headerLength(0, 256)); // aligned size: 48
        directBuffer.putLong(positionOffset(messageOffset(idx)), 3);

        fsLogStorage.open();
        appendedAddress = fsLogStorage.append(writeBuffer);
    }

    @Test
    public void shouldReadAndProcessFirstEvent()
    {
        // given buffer, which could contain first event
        final ByteBuffer readBuffer = ByteBuffer.allocate(ALIGNED_LEN);

        // when read into buffer and buffer was processed
        final long result = fsLogStorage.read(readBuffer, appendedAddress, processor);

        // then
        // result is equal to start address plus event size
        assertThat(result).isEqualTo(appendedAddress + ALIGNED_LEN);
        final DirectBuffer buffer = new UnsafeBuffer(0, 0);
        buffer.wrap(readBuffer);

        // first event was read
        assertThat(buffer.getInt(0)).isEqualTo(LENGTH);
        assertThat(getPosition(buffer, 0)).isEqualTo(1);
    }

    @Test
    public void shouldReadAndProcessTwoEvents()
    {
        // given buffer, which could contain 2 events
        final ByteBuffer readBuffer = ByteBuffer.allocate(2 * ALIGNED_LEN);

        // when read into buffer and buffer was processed
        final long result = fsLogStorage.read(readBuffer, appendedAddress, processor);

        // then
        // returned address is equal to start address plus two event sizes
        assertThat(result).isEqualTo(appendedAddress + ALIGNED_LEN * 2);
        final DirectBuffer buffer = new UnsafeBuffer(0, 0);
        buffer.wrap(readBuffer);

        // first event was read
        assertThat(buffer.getInt(0)).isEqualTo(LENGTH);
        assertThat(getPosition(buffer, 0)).isEqualTo(1);

        // second event was read as well
        assertThat(buffer.getInt(ALIGNED_LEN)).isEqualTo(LENGTH);
        assertThat(getPosition(buffer, ALIGNED_LEN)).isEqualTo(2);
    }

    @Test
    public void shouldTruncateHalfEvent()
    {
        // given buffer, which could contain 1.5 events
        final ByteBuffer readBuffer = ByteBuffer.allocate((int) (ALIGNED_LEN * 1.5));

        // when read into buffer and buffer was processed
        final long result = fsLogStorage.read(readBuffer, appendedAddress, processor);

        // then
        // result is equal to start address plus one event size
        assertThat(result).isEqualTo(appendedAddress + ALIGNED_LEN);
        final DirectBuffer buffer = new UnsafeBuffer(0, 0);
        buffer.wrap(readBuffer);

        // and only first event is read
        assertThat(buffer.getInt(0)).isEqualTo(LENGTH);
        assertThat(getPosition(buffer, 0)).isEqualTo(1);

        // position and limit is reset
        assertThat(readBuffer.position()).isEqualTo(ALIGNED_LEN);
        assertThat(readBuffer.limit()).isEqualTo(ALIGNED_LEN);
    }

    @Test
    public void shouldTruncateEventWithMissingLen()
    {
        // given buffer, which could contain one event and only 3 next bits
        // so not the complete next message len
        final ByteBuffer readBuffer = ByteBuffer.allocate((ALIGNED_LEN + 3));

        // when read into buffer and buffer was processed
        final long result = fsLogStorage.read(readBuffer, appendedAddress, processor);

        // then
        // result is equal to start address plus one event size
        assertThat(result).isEqualTo(appendedAddress + ALIGNED_LEN);
        final DirectBuffer buffer = new UnsafeBuffer(0, 0);
        buffer.wrap(readBuffer);

        // and only first event is read
        assertThat(buffer.getInt(0)).isEqualTo(LENGTH);
        assertThat(getPosition(buffer, 0)).isEqualTo(1);

        // position and limit is reset
        assertThat(readBuffer.position()).isEqualTo(ALIGNED_LEN);
        assertThat(readBuffer.limit()).isEqualTo(ALIGNED_LEN);
    }

    @Test
    public void shouldInsufficientBufferCapacity()
    {
        // given buffer, which could not contain an event
        final ByteBuffer readBuffer = ByteBuffer.allocate((ALIGNED_LEN - 1));

        // when read into buffer and buffer was processed
        final long result = fsLogStorage.read(readBuffer, appendedAddress, processor);

        // then result is OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY
        assertThat(result).isEqualTo(OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY);
    }

    @Test
    public void shouldInsufficientBufferCapacityForLessThenHalfFullBuffer()
    {
        // given buffer
        final ByteBuffer readBuffer = ByteBuffer.allocate(2 * ALIGNED_LEN + ALIGNED_LEN);

        // when read into buffer and buffer was processed
        final long result = fsLogStorage.read(readBuffer, appendedAddress, processor);

        // then only first 2 small events can be read
        // third event was to large, since position is less then remaining bytes,
        // which means buffer is less then half full, OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY will be returned
        assertThat(result).isEqualTo(OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY);
    }

    @Test
    public void shouldTruncateBufferOnHalfBufferWasRead()
    {
        // given buffer
        final ByteBuffer readBuffer = ByteBuffer.allocate(alignedLength(headerLength(0, 256)));

        // when read into buffer and buffer was processed
        final long result = fsLogStorage.read(readBuffer, appendedAddress, processor);

        // then only first 2 small events can be read
        // third event was to large, since position is EQUAL to remaining bytes,
        // which means buffer is half full, the corresponding next address will be returned
        // and block idx can for example be created
        assertThat(result).isEqualTo(appendedAddress + ALIGNED_LEN * 2);
        final DirectBuffer buffer = new UnsafeBuffer(0, 0);
        buffer.wrap(readBuffer);

        // first event was read
        assertThat(buffer.getInt(0)).isEqualTo(LENGTH);
        assertThat(getPosition(buffer, 0)).isEqualTo(1);

        // second event was read as well
        assertThat(buffer.getInt(ALIGNED_LEN)).isEqualTo(LENGTH);
        assertThat(getPosition(buffer, ALIGNED_LEN)).isEqualTo(2);

        // position and limit is reset
        assertThat(readBuffer.position()).isEqualTo(2 * ALIGNED_LEN);
        assertThat(readBuffer.limit()).isEqualTo(2 * ALIGNED_LEN);
    }
}

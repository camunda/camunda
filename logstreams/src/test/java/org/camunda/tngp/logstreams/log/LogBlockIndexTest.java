package org.camunda.tngp.logstreams.log;

import static org.assertj.core.api.Assertions.*;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;
import static org.camunda.tngp.logstreams.log.MockLogStorage.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.logstreams.impl.log.index.LogBlockIndex;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.MockitoAnnotations;

public class LogBlockIndexTest
{
    private static final int CAPACITY = 111;
    private static final int INDEX_BLOCK_SIZE = 1024;

    private LogBlockIndex blockIndex;

    private MockLogStorage mockLogStorage;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup()
    {
        MockitoAnnotations.initMocks(this);

        mockLogStorage = new MockLogStorage();

        blockIndex = createNewBlockIndex(CAPACITY);
    }

    protected LogBlockIndex createNewBlockIndex(int capacity)
    {
        return new LogBlockIndex(capacity, c ->
        {
            return new UnsafeBuffer(ByteBuffer.allocate(c));
        });
    }

    @Test
    public void shouldAddBlock()
    {
        final int capacity = blockIndex.capacity();

        // when

        for (int i = 0; i < capacity; i++)
        {
            final int pos = i + 1;
            final int addr = pos * 10;
            final int expectedSize = i + 1;

            assertThat(blockIndex.addBlock(pos, addr)).isEqualTo(expectedSize);
            assertThat(blockIndex.size()).isEqualTo(expectedSize);
        }

        // then

        for (int i = 0; i < capacity; i++)
        {
            final int virtPos = i + 1;
            final int physPos = virtPos * 10;

            assertThat(blockIndex.getLogPosition(i)).isEqualTo(virtPos);
            assertThat(blockIndex.getAddress(i)).isEqualTo(physPos);
        }

    }

    @Test
    public void shouldNotAddBlockIfCapacityReached()
    {
        // given
        final int capacity = blockIndex.capacity();

        while (capacity > blockIndex.size())
        {
            blockIndex.addBlock(blockIndex.size(), 0);
        }

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage(String.format("LogBlockIndex capacity of %d entries reached. Cannot add new block.", capacity));

        // when
        blockIndex.addBlock(blockIndex.size(), 0);
    }

    @Test
    public void shouldNotAddBlockWithEqualPos()
    {
        // given
        blockIndex.addBlock(10, 0);

        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Illegal value for position");

        // when
        blockIndex.addBlock(10, 0);
    }

    @Test
    public void shouldNotAddBlockWithSmallerPos()
    {
        // given
        blockIndex.addBlock(10, 0);

        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Illegal value for position");

        // when
        blockIndex.addBlock(9, 0);
    }

    @Test
    public void shouldReturnMinusOneForEmptyBlockIndex()
    {
        assertThat(blockIndex.lookupBlockAddress(-1)).isEqualTo(-1);
        assertThat(blockIndex.lookupBlockAddress(1)).isEqualTo(-1);
    }

    @Test
    public void shouldNotReturnFirstBlockIndex()
    {
        // given
        blockIndex.addBlock(10, 1000);

        // then
        for (int i = 0; i < 10; i++)
        {
            assertThat(blockIndex.lookupBlockAddress(i)).isEqualTo(-1);
        }
    }

    @Test
    public void shouldTruncateWithPositionBeforeFirstBlockPosition()
    {
        // given
        final int capacity = blockIndex.capacity();

        for (int i = 0; i < capacity; i++)
        {
            final int pos = (i + 1) * 10;
            final int addr = (i + 1) * 100;

            blockIndex.addBlock(pos, addr);
        }

        // when
        blockIndex.truncate(5);

        // then
        assertThat(blockIndex.size()).isEqualTo(0);

        for (int i = 0; i < capacity; i++)
        {
            final int pos = (i + 1) * 10;
            assertThat(blockIndex.lookupBlockAddress(pos)).isEqualTo(-1L);
            assertThat(blockIndex.lookupBlockPosition(pos)).isEqualTo(-1L);
        }
    }

    @Test
    public void shouldTruncateAtFirstBlock()
    {
        // given
        final int capacity = blockIndex.capacity();

        for (int i = 0; i < capacity; i++)
        {
            final int pos = (i + 1) * 10;
            final int addr = (i + 1) * 100;

            blockIndex.addBlock(pos, addr);
        }

        // when
        blockIndex.truncate(10);

        // then
        assertThat(blockIndex.size()).isEqualTo(0);

        for (int i = 0; i < capacity; i++)
        {
            final int pos = (i + 1) * 10;
            assertThat(blockIndex.lookupBlockAddress(pos)).isEqualTo(-1L);
            assertThat(blockIndex.lookupBlockPosition(pos)).isEqualTo(-1L);
        }
    }

    @Test
    public void shouldTruncateLastBlock()
    {
        // given
        final int capacity = blockIndex.capacity();

        for (int i = 0; i < capacity; i++)
        {
            final int pos = (i + 1) * 10;
            final int addr = (i + 1) * 100;

            blockIndex.addBlock(pos, addr);
        }

        // assume
        assertThat(blockIndex.size()).isEqualTo(111);
        assertThat(blockIndex.lookupBlockAddress(1110)).isEqualTo(11100L);
        assertThat(blockIndex.lookupBlockPosition(1110)).isEqualTo(1110L);

        // when
        blockIndex.truncate(1110);

        // then
        assertThat(blockIndex.size()).isEqualTo(110);
        assertThat(blockIndex.lookupBlockAddress(1110)).isEqualTo(11000L);
        assertThat(blockIndex.lookupBlockPosition(1110)).isEqualTo(1100L);
    }

    @Test
    public void shouldNotTruncateLastBlock()
    {
        // given
        final int capacity = blockIndex.capacity();

        for (int i = 0; i < capacity; i++)
        {
            final int pos = (i + 1) * 10;
            final int addr = (i + 1) * 100;

            blockIndex.addBlock(pos, addr);
        }

        // assume
        assertThat(blockIndex.size()).isEqualTo(111);
        assertThat(blockIndex.lookupBlockAddress(1110)).isEqualTo(11100L);
        assertThat(blockIndex.lookupBlockPosition(1110)).isEqualTo(1110L);

        // when
        blockIndex.truncate(1115);

        // then
        assertThat(blockIndex.size()).isEqualTo(111);
        assertThat(blockIndex.lookupBlockAddress(1110)).isEqualTo(11100L);
        assertThat(blockIndex.lookupBlockPosition(1110)).isEqualTo(1110L);
    }

    @Test
    public void shouldTruncateAtGivenBlockPosition()
    {
        // given
        final int capacity = blockIndex.capacity();

        for (int i = 0; i < capacity; i++)
        {
            final int pos = (i + 1) * 10;
            final int addr = (i + 1) * 100;

            blockIndex.addBlock(pos, addr);
        }

        // when
        blockIndex.truncate(550);

        // then
        final int size = blockIndex.size();
        assertThat(size).isEqualTo(54);

        for (int i = size; i < capacity; i++)
        {
            final int pos = i * 10;
            assertThat(blockIndex.lookupBlockAddress(pos)).isEqualTo(5400L);
            assertThat(blockIndex.lookupBlockPosition(pos)).isEqualTo(540L);
        }
    }

    @Test
    public void shouldTruncateAtNextBlockPosition()
    {
        final int capacity = blockIndex.capacity();

        // when
        for (int i = 0; i < capacity; i++)
        {
            final int pos = (i + 1) * 10;
            final int addr = (i + 1) * 100;

            blockIndex.addBlock(pos, addr);
        }

        // when
        blockIndex.truncate(555);

        // then
        final int size = blockIndex.size();
        assertThat(size).isEqualTo(55);

        for (int i = size; i < capacity; i++)
        {
            final int pos = i * 10;
            assertThat(blockIndex.lookupBlockAddress(pos)).isEqualTo(5500L);
            assertThat(blockIndex.lookupBlockPosition(pos)).isEqualTo(550L);
        }
    }

    @Test
    public void shouldReturnFirstBlockIndex()
    {
        // given
        blockIndex.addBlock(10, 1000);

        // then
        for (int i = 10; i < 100; i++)
        {
            assertThat(blockIndex.lookupBlockAddress(i)).isEqualTo(1000);
        }
    }

    @Test
    public void shouldLookupBlocks()
    {
        final int capacity = blockIndex.capacity();

        // given

        for (int i = 0; i < capacity; i++)
        {
            final int pos = (i + 1) * 10;
            final int addr = (i + 1) * 100;

            blockIndex.addBlock(pos, addr);
        }

        // then

        for (int i = 0; i < capacity; i++)
        {
            final int expectedAddr = (i + 1) * 100;

            for (int j = 0; j < 10; j++)
            {
                final int pos = ((i + 1) * 10) + j;

                assertThat(blockIndex.lookupBlockAddress(pos)).isEqualTo(expectedAddr);
            }
        }
    }

    @Test
    public void shouldNotReturnFirstBlockPosition()
    {
        // given
        blockIndex.addBlock(10, 1000);

        // then
        for (int i = 0; i < 10; i++)
        {
            assertThat(blockIndex.lookupBlockPosition(i)).isEqualTo(-1);
        }
    }

    @Test
    public void shouldReturnFirstBlockPosition()
    {
        // given
        blockIndex.addBlock(10, 1000);

        // then
        for (int i = 10; i < 100; i++)
        {
            assertThat(blockIndex.lookupBlockPosition(i)).isEqualTo(10);
        }
    }

    @Test
    public void shouldLookupBlockPositions()
    {
        final int capacity = blockIndex.capacity();

        // given

        for (int i = 0; i < capacity; i++)
        {
            final int pos = (i + 1) * 10;
            final int addr = (i + 1) * 100;

            blockIndex.addBlock(pos, addr);
        }

        // then

        for (int i = 0; i < capacity; i++)
        {
            final int expectedPos = (i + 1) * 10;

            for (int j = 0; j < 10; j++)
            {
                final int pos = ((i + 1) * 10) + j;

                assertThat(blockIndex.lookupBlockPosition(pos)).isEqualTo(expectedPos);
            }
        }
    }

    @Test
    public void shouldRecoverIndexFromSnapshot() throws Exception
    {
        final int capacity = blockIndex.capacity();

        for (int i = 0; i < capacity; i++)
        {
            final int pos = i + 1;
            final int addr = pos * 10;

            blockIndex.addBlock(pos, addr);
        }

        // when
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blockIndex.writeSnapshot(outputStream);

        final LogBlockIndex newBlockIndex = createNewBlockIndex(CAPACITY);

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        newBlockIndex.recoverFromSnapshot(inputStream);

        // then
        assertThat(newBlockIndex.size()).isEqualTo(blockIndex.size());

        for (int i = 0; i < capacity; i++)
        {
            final int virtPos = i + 1;
            final int physPos = virtPos * 10;

            assertThat(newBlockIndex.getLogPosition(i)).isEqualTo(virtPos);
            assertThat(newBlockIndex.getAddress(i)).isEqualTo(physPos);
        }
    }

    @Test
    public void shouldRecoverIndexFromLogStorage()
    {
        mockLogStorage
            .firstBlockAddress(1L)
            .add(newLogEntry().address(1).position(11).messageLength(INDEX_BLOCK_SIZE / 4).nextAddress(2))
            .add(newLogEntry().address(2).position(12).messageLength(INDEX_BLOCK_SIZE / 4).nextAddress(3))
            .add(newLogEntry().address(3).position(13).messageLength(INDEX_BLOCK_SIZE - HEADER_LENGTH).nextAddress(4))
            .add(newLogEntry().address(4).position(14).messageLength(INDEX_BLOCK_SIZE / 2).nextAddress(5))
            .add(newLogEntry().address(5).position(15).messageLength(INDEX_BLOCK_SIZE - HEADER_LENGTH).nextAddress(6));

        // only create index if index block size is reached
        blockIndex.recover(mockLogStorage.getMock(), INDEX_BLOCK_SIZE);

        assertThat(blockIndex.size()).isEqualTo(2);

        assertThat(blockIndex.getAddress(0)).isEqualTo(3);
        assertThat(blockIndex.getLogPosition(0)).isEqualTo(13);

        assertThat(blockIndex.getAddress(1)).isEqualTo(5);
        assertThat(blockIndex.getLogPosition(1)).isEqualTo(15);
    }

    @Test
    public void shouldRecoverIndexFromLogStorageWithMultipleLogEntriesPerBlock()
    {
        mockLogStorage
            .firstBlockAddress(1)
            .add(newLogEntry().address(1).position(11).messageLength((INDEX_BLOCK_SIZE - HEADER_LENGTH) / 2).nextAddress(2))
            .add(newLogEntries(2).address(2).position(12).messageLength((INDEX_BLOCK_SIZE - HEADER_LENGTH) / 3).nextAddress(4))
            .add(newLogEntry().address(4).position(14).messageLength((INDEX_BLOCK_SIZE - HEADER_LENGTH) / 2).nextAddress(5))
            .add(newLogEntries(3).address(5).position(15).messageLength((INDEX_BLOCK_SIZE - HEADER_LENGTH) / 4).nextAddress(8));

        // create index for first log entry in block if index block size is reached
        blockIndex.recover(mockLogStorage.getMock(), INDEX_BLOCK_SIZE);

        assertThat(blockIndex.size()).isEqualTo(2);

        assertThat(blockIndex.getAddress(0)).isEqualTo(2);
        assertThat(blockIndex.getLogPosition(0)).isEqualTo(12);

        assertThat(blockIndex.getAddress(1)).isEqualTo(5);
        assertThat(blockIndex.getLogPosition(1)).isEqualTo(15);
    }

    @Test
    public void shouldRecoverIndexFromLogStorageWithOverlappingLogEntryMessage()
    {
        mockLogStorage
            .firstBlockAddress(1)
            // second log entry message overlaps the block
            .add(newLogEntries(2).address(1).position(11).messageLength((int) (0.8 * INDEX_BLOCK_SIZE)).nextAddress(3))
            // the remaining part of the log entry
            .add(newLogEntry().address(3).position(12).messageLength((int) (INDEX_BLOCK_SIZE * 0.6)).nextAddress(4))
            // next log entry
            .add(newLogEntry().address(4).position(13).messageLength(INDEX_BLOCK_SIZE - HEADER_LENGTH).nextAddress(5));

        blockIndex.recover(mockLogStorage.getMock(), INDEX_BLOCK_SIZE);

        assertThat(blockIndex.size()).isEqualTo(2);

        assertThat(blockIndex.getAddress(0)).isEqualTo(1);
        assertThat(blockIndex.getLogPosition(0)).isEqualTo(11);

        assertThat(blockIndex.getAddress(1)).isEqualTo(4);
        assertThat(blockIndex.getLogPosition(1)).isEqualTo(13);
    }

    @Test
    public void shouldRecoverIndexFromSnapshotAndLogStorage() throws Exception
    {
        // add one block and create snapshot
        blockIndex.addBlock(11, 1L);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blockIndex.writeSnapshot(outputStream);

        mockLogStorage
            .add(newLogEntry().address(1).position(11).messageLength(INDEX_BLOCK_SIZE - HEADER_LENGTH).nextAddress(2))
            // add one more block after snapshot
            .add(newLogEntry().address(2).position(12).messageLength(INDEX_BLOCK_SIZE - HEADER_LENGTH).nextAddress(3));

        final LogBlockIndex newBlockIndex = createNewBlockIndex(CAPACITY);

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        newBlockIndex.recoverFromSnapshot(inputStream);

        newBlockIndex.recover(mockLogStorage.getMock(), 11, INDEX_BLOCK_SIZE);

        assertThat(newBlockIndex.size()).isEqualTo(2);

        assertThat(newBlockIndex.getAddress(0)).isEqualTo(1);
        assertThat(newBlockIndex.getLogPosition(0)).isEqualTo(11);

        assertThat(newBlockIndex.getAddress(1)).isEqualTo(2);
        assertThat(newBlockIndex.getLogPosition(1)).isEqualTo(12);
    }

}

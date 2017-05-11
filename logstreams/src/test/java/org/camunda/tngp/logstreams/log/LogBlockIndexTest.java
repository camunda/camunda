package org.camunda.tngp.logstreams.log;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.logstreams.impl.log.index.LogBlockIndex;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class LogBlockIndexTest
{
    private static final int CAPACITY = 111;

    private LogBlockIndex blockIndex;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup()
    {
        MockitoAnnotations.initMocks(this);

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

}

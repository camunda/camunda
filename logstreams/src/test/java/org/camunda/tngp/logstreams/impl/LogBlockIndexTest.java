package org.camunda.tngp.logstreams.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class LogBlockIndexTest
{
    LogBlockIndex blockIndex;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup()
    {
        blockIndex = new LogBlockIndex(111, (capacity) ->
        {
            return new UnsafeBuffer(ByteBuffer.allocate(capacity));
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
}

package org.camunda.tngp.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.hashindex.HashIndexDescriptor.BLOCK_DATA_OFFSET;
import static org.camunda.tngp.hashindex.HashIndexDescriptor.framedRecordLength;
import static uk.co.real_logic.agrona.BitUtil.SIZE_OF_LONG;

import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class Bytes2LongHashIndexMinimalBlockSizeTest
{
    static final long MISSING_VALUE = -2;
    static final long DIRTY_VALUE = -3;


    byte[][] keys = new byte[16][64];

    Bytes2LongHashIndex index;
    FileChannelIndexStore indexStore;

    @Before
    public void createIndex()
    {
        final int indexSize = 32;
        final int blockLength = BLOCK_DATA_OFFSET  + framedRecordLength(64, SIZE_OF_LONG);

        indexStore = FileChannelIndexStore.tempFileIndexStore();
        index = new Bytes2LongHashIndex(indexStore, indexSize, blockLength, 64);
        index = new Bytes2LongHashIndex(indexStore);

        // generate keys
        for (int i = 0; i < keys.length; i++)
        {
            final byte[] val = String.valueOf(i).getBytes();

            for (int j = 0; j < val.length; j++)
            {
                keys[i][j] = val[j];
            }
        }
    }

    @After
    public void close()
    {
        indexStore.close();
    }

    @Test
    public void shouldReturnMissingValueForEmptyMap()
    {
        // given that the map is empty
        assertThat(index.get(keys[0], MISSING_VALUE, DIRTY_VALUE) == MISSING_VALUE);
    }

    @Test
    public void shouldReturnMissingValueForNonExistingKey()
    {
        // given
        index.put(keys[1], 1);

        // then
        assertThat(index.get(keys[0], MISSING_VALUE, DIRTY_VALUE) == MISSING_VALUE);
    }

    @Test
    public void shouldReturnLongValueForKey()
    {
        // given
        index.put(keys[1], 1);

        // if then
        assertThat(index.get(keys[1], MISSING_VALUE, DIRTY_VALUE)).isEqualTo(1);
    }

    @Test
    public void shouldReturnLongValueForKeyFromBuffer()
    {
        // given
        index.put(keys[1], 1);

        // if then
        final UnsafeBuffer keyBuffer = new UnsafeBuffer(keys[1]);
        assertThat(index.get(keyBuffer, 0, keyBuffer.capacity(), MISSING_VALUE, DIRTY_VALUE)).isEqualTo(1);
    }

    @Test
    public void shouldSplit()
    {
        // given
        index.put(keys[0], 0);

        // if
        index.put(keys[1], 1);

        // then
        assertThat(index.blockCount()).isEqualTo(2);
        assertThat(index.get(keys[0], MISSING_VALUE, DIRTY_VALUE)).isEqualTo(0);
        assertThat(index.get(keys[1], MISSING_VALUE, DIRTY_VALUE)).isEqualTo(1);
    }


    @Test
    public void shouldPutMultipleValues()
    {
        for (int i = 0; i < 16; i += 2)
        {
            index.put(keys[i], i);
        }

        for (int i = 1; i < 16; i += 2)
        {
            index.put(keys[i], i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(index.get(keys[i], MISSING_VALUE, DIRTY_VALUE) == i);
        }
    }

    @Test
    public void shouldPutValueFromBuffer()
    {
        // when
        final UnsafeBuffer keyBuffer = new UnsafeBuffer(keys[1]);
        index.put(keyBuffer, 0, keyBuffer.capacity(), 1);

        // then
        assertThat(index.get(keys[1], -1L, -2L)).isEqualTo(1L);
    }

    @Test
    public void shouldPutMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            index.put(keys[i], i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(index.get(keys[i], MISSING_VALUE, DIRTY_VALUE) == i);
        }

        assertThat(index.blockCount()).isEqualTo(16);
    }

    @Test
    public void shouldReplaceMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            index.put(keys[i], i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(index.put(keys[i], i)).isTrue();
        }

        assertThat(index.blockCount()).isEqualTo(16);
    }


    @Test
    public void shouldRemoveValueForKey()
    {
        // given
        index.put(keys[1], 1);

        // if
        final long removeResult = index.remove(keys[1], -1);

        //then
        assertThat(removeResult).isEqualTo(1);
        assertThat(index.get(keys[1], -1L, -2L)).isEqualTo(-1L);
    }

    @Test
    public void shouldRemoveValueForKeyFromBuffer()
    {
        // given
        index.put(keys[1], 1);

        // if
        final UnsafeBuffer keyBuffer = new UnsafeBuffer(keys[1]);
        final long removeResult = index.remove(keyBuffer, 0, keyBuffer.capacity(), -1);

        //then
        assertThat(removeResult).isEqualTo(1);
        assertThat(index.get(keys[1], -1L, -2L)).isEqualTo(-1L);
    }

    @Test
    public void shouldNotRemoveValueForDifferentKey()
    {
        // given
        index.put(keys[1], 1);
        index.put(keys[2], 2);

        // if
        final long removeResult = index.remove(keys[1], -1);

        //then
        assertThat(removeResult).isEqualTo(1);
        assertThat(index.get(keys[1], -1, -2)).isEqualTo(-1);
        assertThat(index.get(keys[2], -1, -2)).isEqualTo(2);
    }

}
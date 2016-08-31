package org.camunda.tngp.log;

import static org.agrona.BitUtil.*;

import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.*;
import static org.camunda.tngp.hashindex.HashIndexDescriptor.*;

public class Long2LongHashIndexLargeBlockSizeTest
{
    static final long MISSING_VALUE = -2;

    Long2LongHashIndex index;
    FileChannelIndexStore indexStore;

    @Before
    public void createIndex()
    {
        final int indexSize = 16;
        final int blockLength = BLOCK_DATA_OFFSET  + 3 * framedRecordLength(SIZE_OF_LONG, SIZE_OF_LONG); // 3 entries fit into a block

        indexStore = FileChannelIndexStore.tempFileIndexStore();
        index = new Long2LongHashIndex(indexStore, indexSize, blockLength);
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
        assertThat(index.get(0, MISSING_VALUE) == MISSING_VALUE);
    }

    @Test
    public void shouldReturnMissingValueForNonExistingKey()
    {
        // given
        index.put(1, 1);

        // then
        assertThat(index.get(0, MISSING_VALUE) == MISSING_VALUE);
    }

    @Test
    public void shouldReturnLongValueForKey()
    {
        // given
        index.put(1, 1);

        // if then
        assertThat(index.get(1, MISSING_VALUE)).isEqualTo(1);
    }

    @Test
    public void shouldRemoveValueForKey()
    {
        // given
        index.put(1, 1);

        // if
        final long removeResult = index.remove(1, -1);

        //then
        assertThat(removeResult).isEqualTo(1);
        assertThat(index.get(1, -1)).isEqualTo(-1);
    }

    @Test
    public void shouldNotRemoveValueForDifferentKey()
    {
        // given
        index.put(1, 1);
        index.put(2, 2);

        // if
        final long removeResult = index.remove(1, -1);

        //then
        assertThat(removeResult).isEqualTo(1);
        assertThat(index.get(1, -1)).isEqualTo(-1);
        assertThat(index.get(2, -1)).isEqualTo(2);
    }

    @Test
    public void shouldNotSplitBeforeBlockIsFull()
    {
        // given
        index.put(0, 0);

        // if
        index.put(1, 1);

        // then
        assertThat(index.blockCount()).isEqualTo(1);
        assertThat(index.get(0, MISSING_VALUE)).isEqualTo(0);
        assertThat(index.get(1, MISSING_VALUE)).isEqualTo(1);
    }

    @Test
    public void shouldSplitWhenBlockIsFull()
    {
        // given
        index.put(1, 1);
        index.put(2, 2);
        index.put(3, 3);
        assertThat(index.blockCount()).isEqualTo(1);

        // if
        index.put(4, 4);

        // then
        assertThat(index.blockCount()).isEqualTo(2);
        assertThat(index.get(1, MISSING_VALUE)).isEqualTo(1);
        assertThat(index.get(2, MISSING_VALUE)).isEqualTo(2);
        assertThat(index.get(3, MISSING_VALUE)).isEqualTo(3);
        assertThat(index.get(4, MISSING_VALUE)).isEqualTo(4);
    }

    @Test
    public void shouldSplitMultipleTimesWhenBlockIsFull()
    {
        // given
        index.put(1, 1);
        index.put(3, 3);
        index.put(5, 5);
        assertThat(index.blockCount()).isEqualTo(1);

        // if
        index.put(7, 7);

        // then
        assertThat(index.blockCount()).isEqualTo(3);
        assertThat(index.get(1, MISSING_VALUE)).isEqualTo(1);
        assertThat(index.get(3, MISSING_VALUE)).isEqualTo(3);
        assertThat(index.get(5, MISSING_VALUE)).isEqualTo(5);
        assertThat(index.get(7, MISSING_VALUE)).isEqualTo(7);
    }

    @Test
    public void shouldPutMultipleValues()
    {
        for (int i = 0; i < 16; i += 2)
        {
            index.put(i, i);
        }

        for (int i = 1; i < 16; i += 2)
        {
            index.put(i, i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(index.get(i, MISSING_VALUE) == i);
        }

        assertThat(index.blockCount()).isEqualTo(8);
    }

    @Test
    public void shouldPutMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            index.put(i, i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(index.get(i, MISSING_VALUE) == i);
        }

        assertThat(index.blockCount()).isEqualTo(8);
    }

    @Test
    public void shouldReplaceMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            index.put(i, i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(index.put(i, i)).isTrue();
        }

        assertThat(index.blockCount()).isEqualTo(8);
    }
}
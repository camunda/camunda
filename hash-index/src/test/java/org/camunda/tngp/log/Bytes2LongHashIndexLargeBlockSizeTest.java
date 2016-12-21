package org.camunda.tngp.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class Bytes2LongHashIndexLargeBlockSizeTest
{
    static final long MISSING_VALUE = -2;

    byte[][] keys = new byte[16][64];

    FileChannelIndexStore indexStore;
    Bytes2LongHashIndex index;

    @Before
    public void createIndex() throws IOException
    {
        final int indexSize = 32;

        indexStore = FileChannelIndexStore.tempFileIndexStore();
        index = new Bytes2LongHashIndex(indexStore, indexSize, 3, 64);
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
    public void deleteTempFile()
    {
        indexStore.close();
    }

    @Test
    public void shouldReturnMissingValueForEmptyMap()
    {
        // given that the map is empty
        assertThat(index.get(keys[0], MISSING_VALUE) == MISSING_VALUE);
    }

    @Test
    public void shouldReturnMissingValueForNonExistingKey()
    {
        // given
        index.put(keys[1], 1);

        // then
        assertThat(index.get(keys[0], MISSING_VALUE) == MISSING_VALUE);
    }

    @Test
    public void shouldReturnLongValueForKey()
    {
        // given
        index.put(keys[1], 1);

        // if then
        assertThat(index.get(keys[1], MISSING_VALUE)).isEqualTo(1);
    }

    @Test
    public void shouldNotSplit()
    {
        // given
        index.put(keys[0], 0);

        // if
        index.put(keys[1], 1);

        // then
        assertThat(index.blockCount()).isEqualTo(1);
        assertThat(index.get(keys[0], MISSING_VALUE)).isEqualTo(0);
        assertThat(index.get(keys[1], MISSING_VALUE)).isEqualTo(1);
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
            assertThat(index.get(keys[i], MISSING_VALUE) == i);
        }
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
            assertThat(index.get(keys[i], MISSING_VALUE) == i);
        }
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

        assertThat(index.blockCount()).isEqualTo(6);
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
        assertThat(index.get(keys[1], -1)).isEqualTo(-1);
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
        assertThat(index.get(keys[1], -1)).isEqualTo(-1);
        assertThat(index.get(keys[2], -1)).isEqualTo(2);
    }


}
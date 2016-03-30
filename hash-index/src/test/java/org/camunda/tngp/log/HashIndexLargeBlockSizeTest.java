package org.camunda.tngp.log;

import static uk.co.real_logic.agrona.BitUtil.*;
import static org.mockito.Mockito.*;

import java.nio.ByteBuffer;

import org.camunda.tngp.hashindex.HashIndex;
import org.camunda.tngp.hashindex.IndexValueReader;
import org.camunda.tngp.hashindex.IndexValueWriter;
import org.junit.Before;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

import static org.assertj.core.api.Assertions.*;
import static org.camunda.tngp.hashindex.HashIndexDescriptor.*;

public class HashIndexLargeBlockSizeTest
{
    static long MISSING_VALUE = -2;

    HashIndex index;
    IndexValueReader indexValueReaderMock;
    IndexValueWriter indexValueWriterMock;

    UnsafeBuffer blockBuffer;
    UnsafeBuffer indexBuffer;

    @Before
    public void createIndex()
    {
        int indexSize = 16;
        int blockLength = BLOCK_DATA_OFFSET  + 3 * framedRecordLength(SIZE_OF_LONG); // 3 entries fit into a block
        int valuelenth = 8;

        indexBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(requiredIndexBufferSize(indexSize)));
        blockBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(requiredBlockBufferSize(indexSize, blockLength)));

        index = new HashIndex(indexBuffer, blockBuffer, indexSize, blockLength, valuelenth);

        indexValueReaderMock = mock(IndexValueReader.class);
        indexValueWriterMock = mock(IndexValueWriter.class);
    }

    @Test
    public void shouldReturnMissingValueForEmptyMap()
    {
       // given that the map is empty
       assertThat(index.getLong(0, MISSING_VALUE) == MISSING_VALUE);
    }

    @Test
    public void shouldReturnMissingValueForNonExistingKey()
    {
       // given
       index.put(1, 1);

       // then
       assertThat(index.getLong(0, MISSING_VALUE) == MISSING_VALUE);
    }

    @Test
    public void shouldNotGetValueForEmptyMap()
    {
       // given that the map is empty

       // if a get for a non existing key is attempted
       boolean valueFound = index.get(0, indexValueReaderMock);

       // then
       assertThat(valueFound).isFalse();
       verifyZeroInteractions(indexValueReaderMock);
    }

    @Test
    public void shouldNotGetValueForNonExistingKey()
    {
       // given
       index.put(1, 1);

       // if a get for a non existing key is attempted
       boolean valueFound = index.get(0, indexValueReaderMock);

       // then
       assertThat(valueFound).isFalse();
       verifyZeroInteractions(indexValueReaderMock);
    }

    @Test
    public void shouldReturnLongValueForKey()
    {
        // given
        index.put(1, 1);

        // if then
        assertThat(index.getLong(1, MISSING_VALUE)).isEqualTo(1);
    }

    @Test
    public void shouldReadLongValueForKey()
    {
        // given
        index.put(1, 1);

        // if
        boolean found = index.get(1, indexValueReaderMock);

        // then
        assertThat(found).isEqualTo(true);
        verify(indexValueReaderMock).readValue(eq(blockBuffer), anyInt(), eq(SIZE_OF_LONG));
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
        assertThat(index.getLong(0, MISSING_VALUE)).isEqualTo(0);
        assertThat(index.getLong(1, MISSING_VALUE)).isEqualTo(1);
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
        assertThat(index.getLong(1, MISSING_VALUE)).isEqualTo(1);
        assertThat(index.getLong(2, MISSING_VALUE)).isEqualTo(2);
        assertThat(index.getLong(3, MISSING_VALUE)).isEqualTo(3);
        assertThat(index.getLong(4, MISSING_VALUE)).isEqualTo(4);
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
        assertThat(index.getLong(1, MISSING_VALUE)).isEqualTo(1);
        assertThat(index.getLong(3, MISSING_VALUE)).isEqualTo(3);
        assertThat(index.getLong(5, MISSING_VALUE)).isEqualTo(5);
        assertThat(index.getLong(7, MISSING_VALUE)).isEqualTo(7);
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
            assertThat(index.getLong(i, MISSING_VALUE) == i);
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
            assertThat(index.getLong(i, MISSING_VALUE) == i);
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
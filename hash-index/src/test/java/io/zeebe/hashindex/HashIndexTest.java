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
package io.zeebe.hashindex;

import static io.zeebe.hashindex.HashIndexDescriptor.BLOCK_DATA_OFFSET;
import static io.zeebe.hashindex.HashIndexDescriptor.getRecordLength;
import static io.zeebe.hashindex.HashTableBuckets.ALLOCATION_FACTOR;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.hashindex.types.LongKeyHandler;
import io.zeebe.hashindex.types.LongValueHandler;
import org.agrona.BitUtil;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class HashIndexTest
{


    public static final long KEY = Long.MAX_VALUE;
    public static final long VALUE = Long.MAX_VALUE;
    public static final long MISSING_VALUE = 0;
    public static final int DATA_COUNT = 100_000;

    private Long2LongHashIndex index;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    public static class EvenOddKeyHandler extends LongKeyHandler
    {
        @Override
        public int keyHashCode()
        {
            return (int) theKey & 1;
        }
    }

    @After
    public void tearDown()
    {
        if (index != null)
        {
            index.close();
        }
    }

    public static void putValue(HashIndex<? extends LongKeyHandler, LongValueHandler> hashIndex, long key, long value)
    {
        hashIndex.keyHandler.theKey = key;
        hashIndex.valueHandler.theValue = value;
        hashIndex.put();
    }

    public static long getValue(HashIndex<LongKeyHandler, LongValueHandler> hashIndex, long key, long missingValue)
    {
        hashIndex.keyHandler.theKey = key;
        hashIndex.valueHandler.theValue = missingValue;
        hashIndex.get();
        return hashIndex.valueHandler.theValue;
    }

    @Test
    public void shouldIncreaseHashTable()
    {
        // given
        final HashIndex<LongKeyHandler, LongValueHandler> hashIndex =
            new HashIndex<LongKeyHandler, LongValueHandler>(4, 2, SIZE_OF_LONG, SIZE_OF_LONG) { };
        assertThat(hashIndex.getHashTableSize()).isEqualTo(4 * SIZE_OF_LONG);

        // when
        putValue(hashIndex, 0, 1 << 0);
        putValue(hashIndex, 4, 1 << 1);

            // split
        putValue(hashIndex, 7, 1 << 2);

            // split
        putValue(hashIndex, 9, 1 << 3);

            // split
        putValue(hashIndex, 11, 1 << 4);

            // split & increase hash table
        putValue(hashIndex, 19, 1 << 5);

        // then
        assertThat(hashIndex.getHashTableSize()).isEqualTo(8 * SIZE_OF_LONG);

        assertThat(getValue(hashIndex, 0, -1)).isEqualTo(1 << 0);
        assertThat(getValue(hashIndex, 4, -1)).isEqualTo(1 << 1);
        assertThat(getValue(hashIndex, 7, -1)).isEqualTo(1 << 2);
        assertThat(getValue(hashIndex, 9, -1)).isEqualTo(1 << 3);
        assertThat(getValue(hashIndex, 11, -1)).isEqualTo(1 << 4);
        assertThat(getValue(hashIndex, 19, -1)).isEqualTo(1 << 5);

        // finally
        hashIndex.close();
    }

    @Test
    public void shouldPutNextPowerOfTwoForOddIndexSize()
    {
        // given index not power of two
        final int indexSize = 3;

        // when
        final HashIndex<LongKeyHandler, LongValueHandler> hashIndex =
            new HashIndex<LongKeyHandler, LongValueHandler>(indexSize, 2, SIZE_OF_LONG, SIZE_OF_LONG) { };

        // then index size is set to next power of two
        assertThat(hashIndex.tableSize).isEqualTo(4);

        // and a values can be inserted and read again - put many values to trigger resize
        for (int i = 0; i < 16; i++)
        {
            putValue(hashIndex, i, i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(getValue(hashIndex, i, -1)).isEqualTo(i);
        }
    }

    @Test
    public void shouldPutLargeBunchOfData()
    {
        // given
        final HashIndex<LongKeyHandler, LongValueHandler> hashIndex =
            new HashIndex<LongKeyHandler, LongValueHandler>(4, 1, SIZE_OF_LONG, SIZE_OF_LONG) { };

        // when
        for (int i = 0; i < DATA_COUNT; i++)
        {
            putValue(hashIndex, i, i);
        }

        // then
        assertThat(hashIndex.getHashTableSize()).isEqualTo(BitUtil.findNextPositivePowerOfTwo(DATA_COUNT) * SIZE_OF_LONG);

        for (int i = 0; i < DATA_COUNT; i++)
        {
            assertThat(getValue(hashIndex, i, -1)).isEqualTo(i);
        }

        // finally
        hashIndex.close();
    }

    @Test
    public void shouldThrowExceptionIfTableSizeReachesDefaultMaxSize()
    {
        // given
        final HashIndex<EvenOddKeyHandler, LongValueHandler> hashIndex =
            new HashIndex<EvenOddKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG) { };

        // expect
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Index Full. Cannot resize the hash table to size: " + (1L << 28) +
                                            ", reached max table size of " + HashIndex.MAX_TABLE_SIZE);

        // when
        for (int i = 0; i < DATA_COUNT; i++)
        {
            putValue(hashIndex, i, i);
        }

        hashIndex.close();
    }

    @Test
    public void shouldThrowExceptionIfTableSizeReachesMaxSize()
    {
        // given
        final HashIndex<EvenOddKeyHandler, LongValueHandler> hashIndex =
            new HashIndex<EvenOddKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG) { };
        hashIndex.setMaxTableSize(4);
        for (int i = 0; i < 2; i++)
        {
            putValue(hashIndex, i, i);
        }

        // expect
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Index Full. Cannot resize the hash table to size: " + 8 +
                                            ", reached max table size of " + 4);

        // when
        putValue(hashIndex, 3, 3);

        // finally
        hashIndex.close();
    }

    @Test
    public void shouldUseNextPowerOfTwoForMaxSize()
    {
        // given
        final HashIndex<EvenOddKeyHandler, LongValueHandler> hashIndex =
            new HashIndex<EvenOddKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG) { };

        // when
        hashIndex.setMaxTableSize(3);

        // then
        assertThat(hashIndex.maxTableSize).isEqualTo(4);

        // finally
        hashIndex.close();
    }

    @Test
    public void shouldUseDefaultMaxTableSizeIfGivenSizeIsToLarge()
    {
        // given
        final HashIndex<EvenOddKeyHandler, LongValueHandler> hashIndex =
            new HashIndex<EvenOddKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG) { };

        // when
        hashIndex.setMaxTableSize(1 << 28);

        // then
        assertThat(hashIndex.maxTableSize).isEqualTo(HashIndex.MAX_TABLE_SIZE);

        // finally
        hashIndex.close();
    }

    @Test
    public void shouldOnlyUpdateEntry()
    {
        // given
        final HashIndex<LongKeyHandler, LongValueHandler> hashIndex =
            new HashIndex<LongKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG) { };

        // when
        for (int i = 0; i < 10; i++)
        {
            putValue(hashIndex, 0, i);
        }

        // then
        assertThat(hashIndex.getHashTableSize()).isEqualTo(2 * SIZE_OF_LONG);
        assertThat(hashIndex.blockCount()).isEqualTo(1);
        assertThat(getValue(hashIndex, 0, -1)).isEqualTo(9);
    }

    @Test
    public void shouldRoundToPowerOfTwo()
    {
        // given index not power of two
        final int indexSize = 11;

        // when
        index = new Long2LongHashIndex(indexSize, 1);

        // then index size is set to next power of two
        assertThat(index.tableSize).isEqualTo(16);

        // and a value can be inserted and read again
        index.put(KEY, VALUE);
        assertThat(index.get(KEY, MISSING_VALUE)).isEqualTo(VALUE);
    }

    @Test
    public void shouldUseLimitPowerOfTwo()
    {
        // given index which is higher than the limit 1 << 27
        final int indexSize = 1 << 28;

        // when
        index = new Long2LongHashIndex(indexSize, 1);

        // then index size is set to max value
        assertThat(index.tableSize).isEqualTo(HashIndex.MAX_TABLE_SIZE);

        // and a value can be inserted and read again
        index.put(KEY, VALUE);
        assertThat(index.get(KEY, MISSING_VALUE)).isEqualTo(VALUE);
    }

    @Test
    public void shouldThrowOnRecordLengthOverflow()
    {
        assertThatThrownBy(() ->
            getRecordLength(1, Integer.MAX_VALUE)
        )
            .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
            getRecordLength(Integer.MAX_VALUE, 1)
        )
            .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
            getRecordLength(Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2)
        )
            .isInstanceOf(ArithmeticException.class);
    }

    @Test
    public void shouldThrowOnMaxBlockLengthOverflow()
    {
        assertThatThrownBy(() ->
                new Long2LongHashIndex(1, Integer.MAX_VALUE)
        )
                .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
                new Long2LongHashIndex(1, Integer.MAX_VALUE / 20)
        )
                .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
                new Long2BytesHashIndex(1, Integer.MAX_VALUE, 8)
        )
                .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
                new Long2BytesHashIndex(1, Integer.MAX_VALUE / 20, 8)
        )
                .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
                new Bytes2LongHashIndex(1, Integer.MAX_VALUE, 8)
        )
                .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
                new Bytes2LongHashIndex(1, Integer.MAX_VALUE / 20, 8)
        )
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    public void shouldThrowOnLengthOverflowOnInitialAllocation()
    {
        assertThatThrownBy(() ->
            new Long2LongHashIndex(1, maxRecordPerBlockForLong2LongIndex() + 1)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to allocate index data buffer")
            .hasCauseInstanceOf(ArithmeticException.class);
    }

    private int maxRecordPerBlockForLong2LongIndex()
    {
        return (Integer.MAX_VALUE - BLOCK_DATA_OFFSET - BLOCK_DATA_OFFSET * ALLOCATION_FACTOR) / (getRecordLength(SIZE_OF_LONG, SIZE_OF_LONG) * ALLOCATION_FACTOR);
    }

}

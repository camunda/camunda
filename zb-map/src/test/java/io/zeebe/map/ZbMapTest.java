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
package io.zeebe.map;

import static io.zeebe.map.BucketBufferArray.ALLOCATION_FACTOR;
import static io.zeebe.map.BucketBufferArrayDescriptor.BUCKET_DATA_OFFSET;
import static io.zeebe.map.BucketBufferArrayDescriptor.getBlockLength;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.map.types.*;
import org.agrona.BitUtil;
import org.junit.*;
import org.junit.rules.ExpectedException;

public class ZbMapTest
{

    public static final long KEY = Long.MAX_VALUE;
    public static final long VALUE = Long.MAX_VALUE;
    public static final long MISSING_VALUE = 0;
    public static final int DATA_COUNT = 100_000;

    private ZbMap<LongKeyHandler, LongValueHandler> zbMap;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    public static class EvenOddKeyHandler extends LongKeyHandler
    {
        @Override
        public long keyHashCode()
        {
            return (int) theKey & 1;
        }
    }

    @After
    public void tearDown()
    {
        if (zbMap != null)
        {
            zbMap.close();
        }
    }

    public static void putValue(ZbMap<? extends LongKeyHandler, LongValueHandler> zbMap, long key, long value)
    {
        zbMap.keyHandler.theKey = key;
        zbMap.valueHandler.theValue = value;
        zbMap.put();
    }

    public static long getValue(ZbMap<LongKeyHandler, LongValueHandler> zbMap, long key, long missingValue)
    {
        zbMap.keyHandler.theKey = key;
        zbMap.valueHandler.theValue = missingValue;
        zbMap.get();
        return zbMap.valueHandler.theValue;
    }

    public static boolean removeValue(ZbMap<LongKeyHandler, LongValueHandler> zbMap, long key)
    {
        zbMap.keyHandler.theKey = key;
        return zbMap.remove();
    }

    @Test
    public void shouldIncreaseHashTable()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(4, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };
        assertThat(zbMap.getHashTableSize()).isEqualTo(4 * SIZE_OF_LONG);

        // when
        putValue(zbMap, 0, 1 << 0);
        putValue(zbMap, 4, 1 << 1);

            // split
        putValue(zbMap, 7, 1 << 2);

            // split
        putValue(zbMap, 9, 1 << 3);

            // split
        putValue(zbMap, 11, 1 << 4);

            // split & increase hash table
        putValue(zbMap, 19, 1 << 5);

        // then
        assertThat(zbMap.getHashTableSize()).isEqualTo(8 * SIZE_OF_LONG);

        assertThat(getValue(zbMap, 0, -1)).isEqualTo(1 << 0);
        assertThat(getValue(zbMap, 4, -1)).isEqualTo(1 << 1);
        assertThat(getValue(zbMap, 7, -1)).isEqualTo(1 << 2);
        assertThat(getValue(zbMap, 9, -1)).isEqualTo(1 << 3);
        assertThat(getValue(zbMap, 11, -1)).isEqualTo(1 << 4);
        assertThat(getValue(zbMap, 19, -1)).isEqualTo(1 << 5);

        // finally
        zbMap.close();
    }

    @Test
    public void shouldPutNextPowerOfTwoForOddTableSize()
    {
        // given zbMap not power of two
        final int tableSize = 3;

        // when
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(tableSize, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        // then zbMap size is set to next power of two
        assertThat(zbMap.tableSize).isEqualTo(4);

        // and a values can be inserted and read again - put many values to trigger resize
        for (int i = 0; i < 16; i++)
        {
            putValue(zbMap, i, i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(getValue(zbMap, i, -1)).isEqualTo(i);
        }
    }

    @Test
    public void shouldPutLargeBunchOfData()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(4, 1, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        // when
        for (int i = 0; i < DATA_COUNT; i++)
        {
            putValue(zbMap, i, i);
        }

        // then
        assertThat(zbMap.getHashTableSize()).isEqualTo(BitUtil.findNextPositivePowerOfTwo(DATA_COUNT) * SIZE_OF_LONG);

        for (int i = 0; i < DATA_COUNT; i++)
        {
            assertThat(getValue(zbMap, i, -1)).isEqualTo(i);
        }
    }

    @Test
    public void shouldPutAndRemoveLargeBunchOfData()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(4, 1, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        // when
        for (int i = 0; i < DATA_COUNT; i++)
        {
            putValue(zbMap, i, i);
        }

        // then
        assertThat(zbMap.getHashTableSize()).isEqualTo(BitUtil.findNextPositivePowerOfTwo(DATA_COUNT) * SIZE_OF_LONG);
        assertThat(zbMap.getBucketBufferArray().getBucketBufferCount()).isEqualTo(DATA_COUNT / 32);

        for (int i = 0; i < DATA_COUNT; i++)
        {
            assertThat(removeValue(zbMap, i)).isTrue();
        }
        assertThat(zbMap.getBucketBufferArray().getBucketBufferCount()).isEqualTo(1);
        assertThat(zbMap.getBucketBufferArray().getCapacity()).isEqualTo(zbMap.getBucketBufferArray().getMaxBucketBufferLength());
    }

    @Test
    public void shouldThrowExceptionIfTableSizeReachesDefaultMaxSize()
    {
        // given
        final ZbMap<EvenOddKeyHandler, LongValueHandler> zbMap = new ZbMap<EvenOddKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        // when
        try
        {
            for (int i = 0; i < DATA_COUNT; i++)
            {
                putValue(zbMap, i, i);
            }
        }
        catch (RuntimeException rte)
        {
            assertThat(rte).hasMessage("ZbMap is full. Cannot resize the hash table to size: " + (1L << 28) +
                                           ", reached max table size of " + ZbMap.MAX_TABLE_SIZE);

        }
        finally
        {
            zbMap.close();
        }
    }

    @Test
    public void shouldUseOverflowToAddMoreElements()
    {
        // given entries which all have the same bucket id
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG) { };
        zbMap.setMaxTableSize(8);
        for (int i = 0; i < 4; i++)
        {
            putValue(zbMap, i * 8, i);
        }

        // when
        putValue(zbMap, 32, 4);

        // then overflow was used to add entries
        assertThat(zbMap.bucketCount()).isEqualTo(8);
        assertThat(zbMap.getBucketBufferArray().getBlockCount()).isEqualTo(5);
        for (int i = 0; i <= 4; i++)
        {
            assertThat(getValue(zbMap, i * 8, -1)).isEqualTo(i);
        }
    }

    @Test
    public void shouldDistributeEntriesFromOverflowBuckets()
    {
        // given
        zbMap =
            new ZbMap<LongKeyHandler, LongValueHandler>(8, 1, SIZE_OF_LONG, SIZE_OF_LONG) { };
        putValue(zbMap, 0, 0);
        // split, split, split -> overflow
        putValue(zbMap, 8, 8);
        // fill bucket to reach load factor
        putValue(zbMap, 2, 2);

        // when
        putValue(zbMap, 16, 16);

        // then table is resized, new bucket with id 8 is created during split
        // - entry with key 8 should be relocated
        assertThat(zbMap.getHashTableSize()).isEqualTo(16 * SIZE_OF_LONG);
        assertThat(zbMap.bucketCount()).isEqualTo(6);

        assertThat(getValue(zbMap, 0, -1)).isEqualTo(0);
        assertThat(getValue(zbMap, 8, -1)).isEqualTo(8);
        assertThat(getValue(zbMap, 2, -1)).isEqualTo(2);
        assertThat(getValue(zbMap, 16, -1)).isEqualTo(16);
    }

    @Test
    public void shouldDistributeEntriesFromOverflowBucketsToNewBucketWhichAgainOverflows()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(16, 1, SIZE_OF_LONG, SIZE_OF_LONG)
        { };
        putValue(zbMap, 0, 0);
        // split until bucket id 8 then overflows
        putValue(zbMap, 16, 16);
        // overflows
        putValue(zbMap, 48, 48);

        // fill splitted buckets
        putValue(zbMap, 1, 1);
        putValue(zbMap, 2, 2);
        putValue(zbMap, 4, 4);
        putValue(zbMap, 8, 8);

        // when next value should be added which goes in the first bucket
        putValue(zbMap, 80, 80);

        // then table is resized, new bucket with id 16 is created during split
        // - entries with key 16, 48 should be relocated
        // to add 80, table will be again resized until 48 is relocate and 80 fits into overflow bucket
        assertThat(zbMap.getHashTableSize()).isEqualTo(64 * SIZE_OF_LONG);
        assertThat(zbMap.bucketCount()).isEqualTo(10);

        assertThat(getValue(zbMap, 0, -1)).isEqualTo(0);
        assertThat(getValue(zbMap, 16, -1)).isEqualTo(16);
        assertThat(getValue(zbMap, 48, -1)).isEqualTo(48);
        assertThat(getValue(zbMap, 1, -1)).isEqualTo(1);
        assertThat(getValue(zbMap, 2, -1)).isEqualTo(2);
        assertThat(getValue(zbMap, 4, -1)).isEqualTo(4);
        assertThat(getValue(zbMap, 8, -1)).isEqualTo(8);
        assertThat(getValue(zbMap, 80, -1)).isEqualTo(80);
    }

    @Test
    public void shouldThrowExceptionIfTableSizeReachesMaxSize()
    {
        // given
        // we will add 3 blocks which all zbMap to the idx 0, which is possible with the help of bucket overflow
        // the last buckets get no values - so they will be always free
        // after adding 3 entries we have buckets = entries + 2 (the plus are the free one)
        // this means the load factor of 0.6 will be reached 4/6 = 0.666...
        zbMap =
            new ZbMap<LongKeyHandler, LongValueHandler>(4, 1, SIZE_OF_LONG, SIZE_OF_LONG) { };
        zbMap.setMaxTableSize(4);
        for (int i = 0; i < 3; i++)
        {
            putValue(zbMap, 8 * i, i);
        }

        // expect
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("ZbMap is full. Cannot resize the hash table to size: " + 8 +
                                            ", reached max table size of " + 4);

        // when we now add another entry the table resize will be triggered
        putValue(zbMap, 24, 5);
    }

    @Test
    public void shouldUseNextPowerOfTwoForMaxSize()
    {
        // given
        final ZbMap<EvenOddKeyHandler, LongValueHandler> zbMap = new ZbMap<EvenOddKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        // when
        zbMap.setMaxTableSize(3);

        // then
        assertThat(zbMap.maxTableSize).isEqualTo(4);
    }

    @Test
    public void shouldUseDefaultMaxTableSizeIfGivenSizeIsToLarge()
    {
        // given
        final ZbMap<EvenOddKeyHandler, LongValueHandler> zbMap = new ZbMap<EvenOddKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        // when
        zbMap.setMaxTableSize(1 << 28);

        // then
        assertThat(zbMap.maxTableSize).isEqualTo(ZbMap.MAX_TABLE_SIZE);
    }

    @Test
    public void shouldOnlyUpdateEntry()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        // when
        for (int i = 0; i < 10; i++)
        {
            putValue(zbMap, 0, i);
        }

        // then
        assertThat(zbMap.getHashTableSize()).isEqualTo(2 * SIZE_OF_LONG);
        assertThat(zbMap.bucketCount()).isEqualTo(1);
        assertThat(getValue(zbMap, 0, -1)).isEqualTo(9);
    }

    @Test
    public void shouldRoundToPowerOfTwo()
    {
        // given zbMap not power of two
        final int tableSize = 11;

        // when
        final Long2LongZbMap zbMap = new Long2LongZbMap(tableSize, 1);

        // then zbMap size is set to next power of two
        assertThat(zbMap.tableSize).isEqualTo(16);

        // and a value can be inserted and read again
        zbMap.put(KEY, VALUE);
        assertThat(zbMap.get(KEY, MISSING_VALUE)).isEqualTo(VALUE);
        zbMap.close();
    }

    @Test
    public void shouldUseLimitPowerOfTwo()
    {
        // given zbMap which is higher than the limit 1 << 27
        final int tableSize = 1 << 28;

        // when
        final Long2LongZbMap zbMap = new Long2LongZbMap(tableSize, 1);

        // then zbMap size is set to max value
        assertThat(zbMap.tableSize).isEqualTo(ZbMap.MAX_TABLE_SIZE);

        // and a value can be inserted and read again
        zbMap.put(KEY, VALUE);
        assertThat(zbMap.get(KEY, MISSING_VALUE)).isEqualTo(VALUE);
        zbMap.close();
    }

    @Test
    public void shouldThrowOnRecordLengthOverflow()
    {
        assertThatThrownBy(() ->
            getBlockLength(1, Integer.MAX_VALUE)
        )
            .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
            getBlockLength(Integer.MAX_VALUE, 1)
        )
            .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
            getBlockLength((Integer.MAX_VALUE / 2) + 1, (Integer.MAX_VALUE / 2) + 1)
        )
            .isInstanceOf(ArithmeticException.class);
    }

    @Test
    public void shouldThrowOnMaxBlockLengthOverflow()
    {
        assertThatThrownBy(() ->
                new Long2LongZbMap(1, Integer.MAX_VALUE)
        )
                .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
                new Long2LongZbMap(1, Integer.MAX_VALUE / 16)
        )
                .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
                new Long2BytesZbMap(1, Integer.MAX_VALUE, 8)
        )
                .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
                new Long2BytesZbMap(1, Integer.MAX_VALUE / 16, 8)
        )
                .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
                new Bytes2LongZbMap(1, Integer.MAX_VALUE, 8)
        )
                .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
                new Bytes2LongZbMap(1, Integer.MAX_VALUE / 16, 8)
        )
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    public void shouldThrowOnLengthOverflowOnInitialAllocation()
    {
        assertThatThrownBy(() ->
            new Long2LongZbMap(1, maxRecordPerBlockForLong2Longmap() + 1)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Maximum bucket buffer length exceeds integer maximum value.")
            .hasCauseInstanceOf(ArithmeticException.class);
    }

    private int maxRecordPerBlockForLong2Longmap()
    {
        return (Integer.MAX_VALUE - BUCKET_DATA_OFFSET - BUCKET_DATA_OFFSET * ALLOCATION_FACTOR) / (getBlockLength(SIZE_OF_LONG, SIZE_OF_LONG) * ALLOCATION_FACTOR);
    }

}

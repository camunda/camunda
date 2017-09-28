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
import static io.zeebe.map.BucketBufferArray.getBucketAddress;
import static io.zeebe.map.BucketBufferArrayDescriptor.*;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.*;

import io.zeebe.map.types.LongKeyHandler;
import io.zeebe.map.types.LongValueHandler;
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
    }

    @Test
    public void shouldShrinkHashTable()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(4, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        for (int i = 0; i < 64; i++)
        {
            putValue(zbMap, i, i);
        }

        // when
        for (int i = 63; i > 7; i--)
        {
            removeValue(zbMap, i);
        }
        // if only 25% bucket exist of the table size
        removeValue(zbMap, 7);

        // then table is shrinked
        // TODO: disabled until shrink is fixed see https://github.com/zeebe-io/zeebe/issues/464
        //assertThat(zbMap.getHashTableSize()).isEqualTo(16 * SIZE_OF_LONG);
    }

    @Test
    public void shouldNotShrinkHashTableUnderInitSize()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(4, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        for (int i = 0; i < 64; i++)
        {
            putValue(zbMap, i, i);
        }

        // when
        for (int i = 63; i >= 0; i--)
        {
            removeValue(zbMap, i);
        }

        // then table is shrinked - but only till init table size
        // TODO: disabled until shrink is fixed see https://github.com/zeebe-io/zeebe/issues/464
        //assertThat(zbMap.getHashTableSize()).isEqualTo(4 * SIZE_OF_LONG);
    }

    @Test
    public void shouldNotShrinkHashTable()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(32, 16, SIZE_OF_LONG, SIZE_OF_LONG)
        { };
        putValue(zbMap, 1, 1);

        // when
        assertThat(removeValue(zbMap, 1)).isTrue();

        // then
        assertThat(zbMap.getBucketBufferArray().getBucketBufferCount()).isEqualTo(1);
        assertThat(zbMap.getBucketBufferArray().getCapacity()).isEqualTo(zbMap.getBucketBufferArray().getMaxBucketBufferLength());
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(1);
        assertThat(zbMap.getHashTable().getCapacity()).isEqualTo(32);
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
        assertThat(zbMap.hashTable.getCapacity()).isEqualTo(4);

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
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(4, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        // when
        for (int i = 0; i < DATA_COUNT; i++)
        {
            putValue(zbMap, i, i);
        }

        // then
        assertThat(zbMap.getHashTable().getCapacity()).isEqualTo(BitUtil.findNextPositivePowerOfTwo(DATA_COUNT / 2));
        assertThat(zbMap.getBucketBufferArray().getBucketBufferCount()).isGreaterThanOrEqualTo(DATA_COUNT / 2 / 32);

        for (int i = 0; i < DATA_COUNT; i++)
        {
            assertThat(removeValue(zbMap, i)).isTrue();
        }
        // TODO: disabled until recursive merging is fixed see https://github.com/zeebe-io/zeebe/issues/466
        //assertThat(zbMap.getBucketBufferArray().getBucketBufferCount()).isEqualTo(1);
        //assertThat(zbMap.getBucketBufferArray().getCapacity()).isEqualTo(zbMap.getBucketBufferArray().getMaxBucketBufferLength());
        // TODO: disabled until shrink is fixed see https://github.com/zeebe-io/zeebe/issues/464
        //assertThat(zbMap.getHashTable().getCapacity()).isEqualTo(4);
    }

    @Test
    public void shouldPutRemoveAndPutLargeBunchOfData()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(4, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };
        for (int i = 0; i < DATA_COUNT; i++)
        {
            putValue(zbMap, i, i);
        }

        // when
        for (int i = 0; i < DATA_COUNT; i++)
        {
            assertThat(removeValue(zbMap, i)).isTrue();
        }
        // TODO: disabled until recursive merging is fixed see https://github.com/zeebe-io/zeebe/issues/466
        //assertThat(zbMap.getBucketBufferArray().getBucketBufferCount()).isEqualTo(1);
        //assertThat(zbMap.getBucketBufferArray().getCapacity()).isEqualTo(zbMap.getBucketBufferArray().getMaxBucketBufferLength());
        // TODO: disabled until shrink is fixed see https://github.com/zeebe-io/zeebe/issues/464
        //assertThat(zbMap.getHashTable().getCapacity()).isEqualTo(4);

        // then again put is possible
        for (int i = 0; i < DATA_COUNT; i++)
        {
            putValue(zbMap, i, i);
        }
        assertThat(zbMap.getHashTable().getCapacity()).isEqualTo(BitUtil.findNextPositivePowerOfTwo(DATA_COUNT / 2));
        assertThat(zbMap.getBucketBufferArray().getBucketBufferCount()).isGreaterThanOrEqualTo(DATA_COUNT / 2 / 32);

        // then all content is available
        for (int i = 0; i < DATA_COUNT; i++)
        {
            assertThat(getValue(zbMap, i, -1)).isEqualTo(i);
        }
    }

    @Test
    public void shouldUseOverflowToAddMoreElements()
    {
        // given entries which all have the same bucket id
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG)
        { };
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
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 1, SIZE_OF_LONG, SIZE_OF_LONG)
        { };
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
    public void shouldUseOverflowIfTableSizeReachesMaxSize()
    {
        // given
        // we will add 3 blocks which all zbMap to the idx 0, which is possible with the help of bucket overflow
        // the last buckets get no values - so they will be always free
        // after adding 3 entries we have buckets = entries + 2 (the plus are the free one)
        // this means the load factor of 0.6 will be reached 4/6 = 0.666...
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(4, 1, SIZE_OF_LONG, SIZE_OF_LONG)
        { };
        zbMap.setMaxTableSize(4);
        for (int i = 0; i < 3; i++)
        {
            putValue(zbMap, 8 * i, i);
        }

        // when we now add another entry the table resize will be triggered
        putValue(zbMap, 24, 3);

        // then since max table size is reached overflow will be again used

        // Bucket 0 [0] -> O1
        // Bucket 1 [-]
        // Bucket 2 [-]
        // O1       [8] -> O2
        // O2       [16] -> O3
        // O3       [24]
        assertThat(zbMap.getHashTable().getCapacity()).isEqualTo(4);
        assertThat(zbMap.bucketCount()).isEqualTo(6);

        for (int i = 0; i < 4; i++)
        {
            assertThat(getValue(zbMap, 8 * i, -1)).isEqualTo(i);
        }

    }


    @Test
    public void shouldUseOverflowToAddManyEntriesEvenIfMaxTableSizeIsReached()
    {
        // given
        final ZbMap<LongKeyHandler, LongValueHandler> zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG)
        { };
        zbMap.setMaxTableSize(512);

        // when
        for (int i = 0; i < DATA_COUNT; i++)
        {
            putValue(zbMap, i, i);
        }

        // then
        assertThat(zbMap.getHashTable().getCapacity()).isEqualTo(512);
        assertThat(zbMap.bucketCount()).isEqualTo(DATA_COUNT);

        for (int i = 0; i < DATA_COUNT; i++)
        {
            assertThat(getValue(zbMap, i, -1)).isEqualTo(i);
        }

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
        assertThat(zbMap.hashTable.getCapacity()).isEqualTo(16);

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
        assertThat(zbMap.hashTable.getCapacity()).isEqualTo(ZbMap.MAX_TABLE_SIZE);

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

    @Test
    public void shouldMergeOverflowBuckets()
    {
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0);
        putValue(zbMap, 32, 32);

        // split - relocate - overflow
        putValue(zbMap, 64, 0xFF);
        putValue(zbMap, 128, 0xFF);

        // split - relocate - overflow
        putValue(zbMap, 192, 192);

        // Bucket 0 [0, 32] -> O1
        // Bucket 1 [-, -]
        // Bucket 2 [-, -]
        // Bucket 4 [-, -]
        // O1       [64, 128] -> O2
        // O2       [192, -]
        final BucketBufferArray bucketBufferArray = zbMap.getBucketBufferArray();
        final long firstBucketAddress = getBucketAddress(0, BUCKET_BUFFER_HEADER_LENGTH);
        final long o1Pointer = bucketBufferArray.getBucketOverflowPointer(firstBucketAddress);

        // when
        removeValue(zbMap, 64);
        removeValue(zbMap, 128);

        // then O2 is removed and 192 relocated to first overflow bucket
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(5);
        final long bucketOverflowPointer = bucketBufferArray.getBucketOverflowPointer(firstBucketAddress);
        assertThat(bucketOverflowPointer).isEqualTo(o1Pointer);
        assertThat(bucketBufferArray.getBucketOverflowPointer(bucketOverflowPointer)).isEqualTo(0);

        assertThat(getValue(zbMap, 192, -1)).isEqualTo(192);
        assertThat(getValue(zbMap, 0, -1)).isEqualTo(0);
        assertThat(getValue(zbMap, 32, -1)).isEqualTo(32);
    }

    @Test
    public void shouldMergeOverflowBucketInBetween()
    {
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0);
        putValue(zbMap, 32, 32);

        // split - relocate - overflow
        putValue(zbMap, 64, 0xFF);
        putValue(zbMap, 128, 128);

        // split - relocate - overflow
        putValue(zbMap, 192, 192);

        // Bucket 0 [0, 32] -> O1
        // Bucket 1 [-, -]
        // Bucket 2 [-, -]
        // Bucket 4 [-, -]
        // O1       [64, 128] -> O2
        // O2       [192, -]
        final BucketBufferArray bucketBufferArray = zbMap.getBucketBufferArray();
        final long firstBucketAddress = getBucketAddress(0, BUCKET_BUFFER_HEADER_LENGTH);
        final long o1Pointer = bucketBufferArray.getBucketOverflowPointer(firstBucketAddress);
        final long o2Pointer = bucketBufferArray.getBucketOverflowPointer(o1Pointer);

        // when
        removeValue(zbMap, 32);
        removeValue(zbMap, 64);
        removeValue(zbMap, 0);

        // then O1 is marked as removable
        // 192 moved to bucket 0 -> overflow pointer is replaced with pointer to O2
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(6);
        final long bucketOverflowPointer = bucketBufferArray.getBucketOverflowPointer(firstBucketAddress);
        assertThat(bucketOverflowPointer).isNotEqualTo(o1Pointer);
        assertThat(bucketOverflowPointer).isEqualTo(o2Pointer);
        assertThat(bucketBufferArray.getBucketOverflowPointer(bucketOverflowPointer)).isEqualTo(0);

        assertThat(getValue(zbMap, 192, -1)).isEqualTo(192);
        assertThat(getValue(zbMap, 128, -1)).isEqualTo(128);
    }

    @Test
    public void shouldRemoveEmptyOverflowBucketOnBlockRemoveOfParentOverflowBucket()
    {
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0);
        putValue(zbMap, 32, 32);

        // split - relocate - overflow
        putValue(zbMap, 64, 64);
        putValue(zbMap, 128, 0xFF);

        // split - relocate - overflow
        putValue(zbMap, 192, 0xFF);

        // Bucket 0 [0, 32] -> O1
        // Bucket 1 [-, -]
        // Bucket 2 [-, -]
        // Bucket 4 [-, -]
        // O1       [64, 128] -> O2
        // O2       [192, -]
        final BucketBufferArray bucketBufferArray = zbMap.getBucketBufferArray();
        final long firstBucketAddress = getBucketAddress(0, BUCKET_BUFFER_HEADER_LENGTH);
        final long o1Pointer = bucketBufferArray.getBucketOverflowPointer(firstBucketAddress);
        final long o2Pointer = bucketBufferArray.getBucketOverflowPointer(o1Pointer);

        // when
        removeValue(zbMap, 192);

        // then
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(6);
        long bucketOverflowPointer = bucketBufferArray.getBucketOverflowPointer(firstBucketAddress);
        assertThat(bucketOverflowPointer).isEqualTo(o1Pointer);
        assertThat(bucketBufferArray.getBucketOverflowPointer(bucketOverflowPointer)).isEqualTo(o2Pointer);

        // when
        removeValue(zbMap, 128);

        // then O2 is removed
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(5);
        bucketOverflowPointer = bucketBufferArray.getBucketOverflowPointer(firstBucketAddress);
        assertThat(bucketOverflowPointer).isEqualTo(o1Pointer);
        assertThat(bucketBufferArray.getBucketOverflowPointer(bucketOverflowPointer)).isEqualTo(0);

        assertThat(getValue(zbMap, 64, -1)).isEqualTo(64);
        assertThat(getValue(zbMap, 0, -1)).isEqualTo(0);
        assertThat(getValue(zbMap, 32, -1)).isEqualTo(32);
    }

    @Test
    public void shouldRemoveEmptyOverflowBucketOnBlockRemoveOfOriginalBucket()
    {
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0);
        putValue(zbMap, 32, 32);

        // split - relocate - overflow
        putValue(zbMap, 64, 64);
        putValue(zbMap, 128, 128);

        // split - relocate - overflow
        putValue(zbMap, 192, 0xFF);

        // Bucket 0 [0, 32] -> O1
        // Bucket 1 [-, -]
        // Bucket 2 [-, -]
        // Bucket 4 [-, -]
        // O1       [64, 128] -> O2
        // O2       [192, -]
        final BucketBufferArray bucketBufferArray = zbMap.getBucketBufferArray();
        final long firstBucketAddress = getBucketAddress(0, BUCKET_BUFFER_HEADER_LENGTH);
        final long o1Pointer = bucketBufferArray.getBucketOverflowPointer(firstBucketAddress);
        final long o2Pointer = bucketBufferArray.getBucketOverflowPointer(o1Pointer);

        // when
        removeValue(zbMap, 32);

        // then
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(6);
        long bucketOverflowPointer = bucketBufferArray.getBucketOverflowPointer(firstBucketAddress);
        assertThat(bucketOverflowPointer).isEqualTo(o1Pointer);
        assertThat(bucketBufferArray.getBucketOverflowPointer(bucketOverflowPointer)).isEqualTo(o2Pointer);

        // when
        removeValue(zbMap, 192);

        // then O2 is removed
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(5);
        bucketOverflowPointer = bucketBufferArray.getBucketOverflowPointer(firstBucketAddress);
        assertThat(bucketOverflowPointer).isEqualTo(o1Pointer);
        assertThat(bucketBufferArray.getBucketOverflowPointer(bucketOverflowPointer)).isEqualTo(0);

        assertThat(getValue(zbMap, 64, -1)).isEqualTo(64);
        assertThat(getValue(zbMap, 0, -1)).isEqualTo(0);
        assertThat(getValue(zbMap, 128, -1)).isEqualTo(128);
    }

    @Test
    public void shouldNotMergeOverflowBucket()
    {
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0xFF);
        putValue(zbMap, 8, 0xFF);

        // split - relocate - overflow
        putValue(zbMap, 16, 0xFF);

        // put 1, 9 in B1
        putValue(zbMap, 1, 0xFF);
        putValue(zbMap, 9, 0xFF);

        // split - relocate - overflow
        putValue(zbMap, 17, 0xFF);

        // Bucket 0 [0, 8] -> O1
        // Bucket 1 [1, 9] -> O2
        // Bucket 2 [-, -]
        // Bucket 4 [-, -]
        // O1       [16, -]
        // Bucket 3 [-, -]
        // Bucket 5 [-, -]
        // O2       [17, -]
        final BucketBufferArray bucketBufferArray = zbMap.getBucketBufferArray();

        // when
        removeValue(zbMap, 16);

        // then block is removed but overflow bucket is not merged since
        // original bucket is full
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(8);
        final long firstBucketAddress = getBucketAddress(0, BUCKET_BUFFER_HEADER_LENGTH);
        assertThat(bucketBufferArray.getBucketOverflowPointer(firstBucketAddress)).isGreaterThan(0);
        final long secondBucketAddress = getBucketAddress(0, BUCKET_BUFFER_HEADER_LENGTH + bucketBufferArray.getMaxBucketLength());
        assertThat(bucketBufferArray.getBucketOverflowPointer(secondBucketAddress)).isGreaterThan(0);
    }

    @Test
    public void shouldNotMergeOverflowBucketEvenIfLastBucket()
    {
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0xFF);
        putValue(zbMap, 8, 0xFF);

        // split - relocate - overflow
        putValue(zbMap, 16, 0xFF);

        // put 1, 9 in B1
        putValue(zbMap, 1, 0xFF);
        putValue(zbMap, 9, 0xFF);

        // split - relocate - overflow
        putValue(zbMap, 17, 0xFF);

        // Bucket 0 [0, 8] -> O1
        // Bucket 1 [1, 9] -> O2
        // Bucket 2 [-, -]
        // Bucket 4 [-, -]
        // O1       [16, -]
        // Bucket 3 [-, -]
        // Bucket 5 [-, -]
        // O2       [17, -]
        final BucketBufferArray bucketBufferArray = zbMap.getBucketBufferArray();

        // when
        removeValue(zbMap, 17);

        // then block is removed but overflow bucket is not merged since
        // original bucket is full
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(8);
        final long firstBucketAddress = getBucketAddress(0, BUCKET_BUFFER_HEADER_LENGTH);
        assertThat(bucketBufferArray.getBucketOverflowPointer(firstBucketAddress)).isGreaterThan(0);
        final long secondBucketAddress = getBucketAddress(0, BUCKET_BUFFER_HEADER_LENGTH + bucketBufferArray.getMaxBucketLength());
        assertThat(bucketBufferArray.getBucketOverflowPointer(secondBucketAddress)).isGreaterThan(0);
    }

    @Test
    public void shouldRelocateBlocksOfOverflowBucket()
    {
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };


        putValue(zbMap, 0, 0xFF);
        putValue(zbMap, 8, 0xFF);

        // split - relocate - overflow
        putValue(zbMap, 16, 0xFF);
        putValue(zbMap, 24, 24);

        // put 1, 9 in B1
        putValue(zbMap, 1, 0xFF);
        putValue(zbMap, 9, 0xFF);

        // split - relocate - overflow
        putValue(zbMap, 17, 0xFF);


        // Bucket 0 [0, 8] -> O1
        // Bucket 1 [1, 9] -> O2
        // Bucket 2 [-, -]
        // Bucket 4 [-, -]
        // O1       [16, 24]
        // Bucket 3 [-, -]
        // Bucket 5 [-, -]
        // O2       [17, -]
        final BucketBufferArray bucketBufferArray = zbMap.getBucketBufferArray();

        // when
        removeValue(zbMap, 0);
        removeValue(zbMap, 8);
        removeValue(zbMap, 16);

        // then blocks of overflow bucket are relocated to original bucket
        // overflow bucket is marked as removable
        // overflow pointer is removed
        assertThat(bucketBufferArray
                       .getBucketCount()).isEqualTo(8);
        final long firstBucketAddress = getBucketAddress(0, BUCKET_BUFFER_HEADER_LENGTH);
        assertThat(bucketBufferArray.getBucketOverflowPointer(firstBucketAddress)).isEqualTo(0);
        final long secondBucketAddress = getBucketAddress(0, BUCKET_BUFFER_HEADER_LENGTH + bucketBufferArray.getMaxBucketLength());
        assertThat(bucketBufferArray.getBucketOverflowPointer(secondBucketAddress)).isGreaterThan(0);

        // block is still available
        assertThat(getValue(zbMap, 24, -1)).isEqualTo(24);
    }

    @Test
    public void shouldRelocateBlocksOfOverflowBucketAndRemoveOverflowBucket()
    {
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };


        putValue(zbMap, 0, 0xFF);
        putValue(zbMap, 8, 0xFF);

        // split - relocate - overflow
        putValue(zbMap, 16, 16);

        // Bucket 0 [0, 8] -> O1
        // Bucket 1 [-, -] -> O2
        // Bucket 2 [-, -]
        // Bucket 4 [-, -]
        // O1       [16, -]
        final BucketBufferArray bucketBufferArray = zbMap.getBucketBufferArray();

        // when
        removeValue(zbMap, 0);
        removeValue(zbMap, 8);

        // then blocks of overflow bucket are relocated to original bucket
        // overflow bucket is marked as removable
        // overflow pointer is removed
        // TODO: disabled until recursive merging is fixed see https://github.com/zeebe-io/zeebe/issues/466
        //assertThat(bucketBufferArray.getBucketCount()).isEqualTo(1);
        final long firstBucketAddress = getBucketAddress(0, BUCKET_BUFFER_HEADER_LENGTH);
        assertThat(bucketBufferArray.getBucketOverflowPointer(firstBucketAddress)).isEqualTo(0);

        // block is still available
        assertThat(getValue(zbMap, 16, -1)).isEqualTo(16);
    }

    @Test
    public void shouldMergeRecursivelyAndRemoveOverflowBucketFromOverflowBucket()
    {
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };


        putValue(zbMap, 0, 0);
        putValue(zbMap, 8, 0xFF);

        // split - relocate - overflow
        putValue(zbMap, 16, 0xFF);
        putValue(zbMap, 24, 24);

        // put 1, 9 in B1
        putValue(zbMap, 1, 1);
        putValue(zbMap, 9, 0xFF);

        // split - relocate - overflow
        putValue(zbMap, 17, 0xFF);


        // Bucket 0 [0, 8] -> O1
        // Bucket 1 [1, 9] -> O2
        // Bucket 2 [-, -]
        // Bucket 4 [-, -]
        // O1       [16, 24]
        // Bucket 3 [-, -]
        // Bucket 5 [-, -]
        // O2       [17, -]
        final BucketBufferArray bucketBufferArray = zbMap.getBucketBufferArray();

        // when
        removeValue(zbMap, 0);
        removeValue(zbMap, 8);
        removeValue(zbMap, 16);


        // if block with key 9 is not removed overflow bucket will not been merged since original bucket is still full
        removeValue(zbMap, 9);
        removeValue(zbMap, 17);

        // on removing 17 an merge process will be triggered
        // this merges 02 with bucket 1
        // merges 5, 3 with 1
        // removes 01 since it is marked as removable
        // merges 4 and 2 with 0
        // end at bucket 1 since it has one block and bucket 0 as well

        // TODO: disabled until recursive merging is fixed see https://github.com/zeebe-io/zeebe/issues/466
        //assertThat(bucketBufferArray.getBucketCount()).isEqualTo(2);
        final long firstBucketAddress = getBucketAddress(0, BUCKET_BUFFER_HEADER_LENGTH);
        assertThat(bucketBufferArray.getBucketOverflowPointer(firstBucketAddress)).isEqualTo(0);
        final long secondBucketAddress = getBucketAddress(0, BUCKET_BUFFER_HEADER_LENGTH + bucketBufferArray.getMaxBucketLength());
        assertThat(bucketBufferArray.getBucketOverflowPointer(secondBucketAddress)).isEqualTo(0);

        // block is still available
        assertThat(getValue(zbMap, 24, -1)).isEqualTo(24);
        assertThat(getValue(zbMap, 1, -1)).isEqualTo(1);
    }

    @Test
    public void shouldMergeRecursivelyAndRemoveOverflowBucketFromOriginalBucket()
    {
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };


        putValue(zbMap, 0, 0);
        putValue(zbMap, 8, 0xFF);

        // split - relocate - overflow
        putValue(zbMap, 16, 0xFF);
        putValue(zbMap, 24, 24);

        // put 1, 9 in B1
        putValue(zbMap, 1, 1);
        putValue(zbMap, 9, 0xFF);

        // split - relocate - overflow
        putValue(zbMap, 17, 17);

        // Bucket 0 [0, 8] -> O1
        // Bucket 1 [1, 9] -> O2
        // Bucket 2 [-, -]
        // Bucket 4 [-, -]
        // O1       [16, 24]
        // Bucket 3 [-, -]
        // Bucket 5 [-, -]
        // O2       [17, -]
        final BucketBufferArray bucketBufferArray = zbMap.getBucketBufferArray();

        // when
        removeValue(zbMap, 0);
        removeValue(zbMap, 8);
        removeValue(zbMap, 16);

        // if block with key 9 is not removed overflow bucket will not been merged since original bucket is still full
        removeValue(zbMap, 9);
        removeValue(zbMap, 1);

        // on removing 1 an merge process will be triggered
        // this merges 02 with bucket 1 -> relocate 17 to bucket 1
        // merges 5, 3 with 1
        // removes 01 since it is marked as removable
        // merges 4 and 2 with 0
        // end at bucket 1 since it has one block and bucket 0 as well

        // TODO: disabled until recursive merging is fixed see https://github.com/zeebe-io/zeebe/issues/466
        //assertThat(bucketBufferArray.getBucketCount()).isEqualTo(2);
        final long firstBucketAddress = getBucketAddress(0, BUCKET_BUFFER_HEADER_LENGTH);
        assertThat(bucketBufferArray.getBucketOverflowPointer(firstBucketAddress)).isEqualTo(0);
        final long secondBucketAddress = getBucketAddress(0, BUCKET_BUFFER_HEADER_LENGTH + bucketBufferArray.getMaxBucketLength());
        assertThat(bucketBufferArray.getBucketOverflowPointer(secondBucketAddress)).isEqualTo(0);

        // block is still available
        assertThat(getValue(zbMap, 24, -1)).isEqualTo(24);
        assertThat(getValue(zbMap, 17, -1)).isEqualTo(17);
    }

    @Test
    public void shouldMergeChildBucket()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0xFF);
        putValue(zbMap, 1, 0xFF);

        // split - relocate 1
        putValue(zbMap, 2, 0xFF);

        // split - relocate 2
        putValue(zbMap, 8, 0xFF);

        putValue(zbMap, 3, 0xFF);
        // split - relocate 3
        putValue(zbMap, 5, 0xFF);

        // split - relocate 5
        putValue(zbMap, 9, 0xFF);

        // Bucket 0 [0, 8]
        // Bucket 1 [1, 9]
        // Bucket 2 [2, -]
        // Bucket 3 [3, -]
        // Bucket 5 [5, -]

        // when
        removeValue(zbMap, 9);

        // then Bucket 1 is half full but merge should not happen since then bucket is again full
        // makes no sense to merge since after that an next add we have to split again
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(5);

        // when
        removeValue(zbMap, 5);

        // then Bucket 5 is empty - merging 5 with 1
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(4);
    }

    @Test
    public void shouldMergeBucketsWhenEmpty()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 4, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0);
        putValue(zbMap, 1, 1);
        putValue(zbMap, 2, 2);
        putValue(zbMap, 3, 3);

        // split
        putValue(zbMap, 5, 5);
        putValue(zbMap, 7, 7);

        // Bucket 0 [0, 2, _, _]
        // Bucket 1 [1, 3, 5, 7]

        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(2);

        // when
        removeValue(zbMap, 2);
        removeValue(zbMap, 0);
        // then bucket 0 is empty but merge should not happen since then bucket is again full
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(2);

        // when remove one more entry
        removeValue(zbMap, 7);
        // then merge buckets together
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(1);

        assertThat(getValue(zbMap, 1, -1)).isEqualTo(1);
        assertThat(getValue(zbMap, 3, -1)).isEqualTo(3);
        assertThat(getValue(zbMap, 5, -1)).isEqualTo(5);
    }

    @Test
    public void shouldMergeParentWithChildBucket()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0);
        putValue(zbMap, 1, 1);

        // split - relocate 1
        putValue(zbMap, 2, 2);

        // split - relocate 2
        putValue(zbMap, 8, 8);

        putValue(zbMap, 3, 3);
        // split - relocate 3
        putValue(zbMap, 5, 5);

        // split - relocate 5
        putValue(zbMap, 9, 9);

        // Bucket 0 [0, 8]
        // Bucket 1 [1, 9]
        // Bucket 2 [2, -]
        // Bucket 3 [3, -]
        // Bucket 5 [5, -]

        // when
        removeValue(zbMap, 9);

        // then Bucket 1 is half full but merge should not happen since then bucket is again full
        // makes no sense to merge since after that an next add we have to split again
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(5);

        // when
        removeValue(zbMap, 1);

        // then Bucket 1 is empty - merging 5 with 1
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(4);
        assertThat(getValue(zbMap, 0, -1)).isEqualTo(0);
        assertThat(getValue(zbMap, 2, -1)).isEqualTo(2);
        assertThat(getValue(zbMap, 8, -1)).isEqualTo(8);
        assertThat(getValue(zbMap, 3, -1)).isEqualTo(3);
        assertThat(getValue(zbMap, 5, -1)).isEqualTo(5);
    }

    @Test
    public void shouldMergeParentWithChildBucketsRecursively()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0);
        putValue(zbMap, 1, 1);

        // split - relocate 1
        putValue(zbMap, 2, 2);

        // split - relocate 2
        putValue(zbMap, 8, 8);

        putValue(zbMap, 3, 3);
        // split - relocate 3
        putValue(zbMap, 5, 5);

        // split - relocate 5
        putValue(zbMap, 9, 9);

        // Bucket 0 [0, 8]
        // Bucket 1 [1, 9]
        // Bucket 2 [2, -]
        // Bucket 3 [3, -]
        // Bucket 5 [5, -]

        // when
        removeValue(zbMap, 9);
        // then Bucket 1 is half full but merge should not happen since then bucket is again full
        // makes no sense to merge since after that an next add we have to split again
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(5);

        // when
        removeValue(zbMap, 3);
        // then Bucket 3 is empty
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(5);

        // when
        removeValue(zbMap, 1);
        // then Bucket 1 is empty - merging 5 with 1 and also 3 with 1 since Bucket 3 is empty
        // TODO: disabled until recursive merging is fixed see https://github.com/zeebe-io/zeebe/issues/466
        //assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(3);
        assertThat(getValue(zbMap, 0, -1)).isEqualTo(0);
        assertThat(getValue(zbMap, 2, -1)).isEqualTo(2);
        assertThat(getValue(zbMap, 8, -1)).isEqualTo(8);
        assertThat(getValue(zbMap, 5, -1)).isEqualTo(5);
    }

    @Test
    public void shouldMergeBuckets()
    {
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0);
        putValue(zbMap, 1, 1);

        // split - relocate 1
        putValue(zbMap, 2, 2);

        // split - relocate 2
        putValue(zbMap, 8, 8);

        putValue(zbMap, 3, 3);
        // split - relocate 3
        putValue(zbMap, 5, 5);

        // split - relocate 5
        putValue(zbMap, 9, 9);


        // Bucket 0 [0, 8]
        // Bucket 1 [1, 9]
        // Bucket 2 [2, -]
        // Bucket 3 [3, -]
        // Bucket 5 [5, -]

        // when
        removeValue(zbMap, 9);
        removeValue(zbMap, 8);
        // -> Bucket 0 and 1 is half full - can't be merged with 2 or 5 since bucket will be again full
        removeValue(zbMap, 2);
        // -> Bucket 2 is empty - can't be merged
        removeValue(zbMap, 3);
        // -> Bucket 3 is empty - can't be merged
        removeValue(zbMap, 5);

        // then Bucket 5 is empty - merging 5, 3 and 2 since bucket 1 and 0 is half full
        // TODO: disabled until recursive merging is fixed see https://github.com/zeebe-io/zeebe/issues/466
        //assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(2);
        assertThat(getValue(zbMap, 0, -1)).isEqualTo(0);
        assertThat(getValue(zbMap, 1, -1)).isEqualTo(1);
    }

    @Test
    public void shouldNotMergeOnDifferentDepths()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0xFF);
        putValue(zbMap, 1, 0xFF);

        // split - relocate 1
        putValue(zbMap, 2, 0xFF);

        // split - relocate 2
        putValue(zbMap, 8, 0xFF);

        putValue(zbMap, 3, 0xFF);
        // split - relocate 3
        putValue(zbMap, 5, 0xFF);

        // split - relocate 5
        putValue(zbMap, 9, 0xFF);

        // Bucket 0 [0, 8]
        // Bucket 1 [1, 9]
        // Bucket 2 [2, -]
        // Bucket 3 [3, -]
        // Bucket 5 [5, -]

        // when
        removeValue(zbMap, 9);
        removeValue(zbMap, 3);

        // then bucket 3 can't be merged - since bucket 3 depth is 2 and bucket 1 depth is 3
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(5);
    }

    @Test
    public void shouldNotMergeIfNotLastBucket()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0xFF);
        putValue(zbMap, 1, 0xFF);

        // split - relocate 1
        putValue(zbMap, 2, 0xFF);

        // split - relocate 2
        putValue(zbMap, 8, 0xFF);

        putValue(zbMap, 3, 0xFF);
        // split - relocate 3
        putValue(zbMap, 5, 0xFF);

        // split - relocate 5
        putValue(zbMap, 9, 0xFF);

        // Bucket 0 [0, 8]
        // Bucket 1 [1, 9]
        // Bucket 2 [2, -]
        // Bucket 3 [3, -]
        // Bucket 5 [5, -]

        // when
        removeValue(zbMap, 8);
        removeValue(zbMap, 2);

        // then bucket 2 can't be merged - since bucket 2 is not removable
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(5);
    }

    @Test
    public void shouldNotMergeIfNotHalfFull()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 3, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0xFF);
        putValue(zbMap, 1, 0xFF);
        putValue(zbMap, 3, 0xFF);

        // split - relocate 1, 3, 9
        putValue(zbMap, 9, 0xFF);

        // split - relocate 3
        putValue(zbMap, 17, 0xFF);
        putValue(zbMap, 11, 0xFF);
        putValue(zbMap, 19, 0xFF);

        // Bucket 0 [0, -, -]
        // Bucket 1 [1, 9, 17]
        // Bucket 3 [3, 11, 19]

        // when
        removeValue(zbMap, 19);

        // then bucket 3 can't be merged - since bucket 3 is not leq half full
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(3);
    }

    @Test
    public void shouldNotMergeIfParentBucketAfterMergeIsLessThenFull()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 3, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0xFF);
        putValue(zbMap, 1, 0xFF);
        putValue(zbMap, 3, 0xFF);

        // split - relocate 1, 3, 9
        putValue(zbMap, 9, 0xFF);

        // split - relocate 3
        putValue(zbMap, 17, 0xFF);

        // Bucket 0 [0, -, -]
        // Bucket 1 [1, 9, 17]
        // Bucket 3 [3, -, -]

        // when
        removeValue(zbMap, 3);

        // then bucket 3 can't be merged - since bucket 1 + blocks of 3 is not less then full bucket
        // even if bucket 3 is empty this will cause merge and split again if a new value is added
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(3);
    }

    @Test
    public void shouldNotMergeIfOnlyOneBucket()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 3, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0xFF);
        putValue(zbMap, 1, 0xFF);

        // Bucket 0 [0, 1, -]

        // when
        removeValue(zbMap, 1);

        // then merge should not be triggered
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(1);
    }

    @Test
    public void shouldNotMergeBuckets()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 2, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        putValue(zbMap, 0, 0xFF);
        putValue(zbMap, 1, 0xFF);

        // split - relocate 1
        putValue(zbMap, 2, 0xFF);

        // split - relocate 2
        putValue(zbMap, 8, 0xFF);

        putValue(zbMap, 3, 0xFF);
        // split - relocate 3
        putValue(zbMap, 5, 0xFF);

        // split - relocate 5
        putValue(zbMap, 9, 0xFF);


        // Bucket 0 [0, 8]
        // Bucket 1 [1, 9]
        // Bucket 2 [2, -]
        // Bucket 3 [3, -]
        // Bucket 5 [5, -]

        // when
        removeValue(zbMap, 2);
        // -> Bucket 2 is empty - can't be merged
        removeValue(zbMap, 3);
        // -> Bucket 3 is empty - can't be merged
        removeValue(zbMap, 5);

        // then Bucket 5 is empty - not merging 5 since bucket 1 is not half full
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(5);
    }

    @Test
    public void shouldPutAndRemoveRandomValues()
    {
        // given
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 4, SIZE_OF_LONG, SIZE_OF_LONG)
        { };

        final Set<Long> values = new HashSet<>();

        final Random random = new Random(100);

        while (values.size() < 100_000)
        {
            final long i = Math.abs(random.nextLong());

            putValue(zbMap, i, i);

            values.add(i);
        }

        int removedValues = 0;

        for (Long value : values)
        {
            final boolean removed = removeValue(zbMap, value);

            if (removed)
            {
                removedValues += 1;
            }
        }

        // block count is equal to the missing values
        assertThat(removedValues).isEqualTo(values.size());
    }

    private int maxRecordPerBlockForLong2Longmap()
    {
        return (Integer.MAX_VALUE - BUCKET_DATA_OFFSET - BUCKET_DATA_OFFSET * ALLOCATION_FACTOR) / (getBlockLength(SIZE_OF_LONG, SIZE_OF_LONG) * ALLOCATION_FACTOR);
    }

}

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

import static io.zeebe.map.ZbMapDescriptor.BUCKET_DATA_OFFSET;
import static io.zeebe.map.ZbMapDescriptor.getBlockLength;
import static io.zeebe.map.BucketArray.ALLOCATION_FACTOR;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.map.types.LongKeyHandler;
import io.zeebe.map.types.LongValueHandler;
import org.agrona.BitUtil;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ZbMapTest
{

    public static final long KEY = Long.MAX_VALUE;
    public static final long VALUE = Long.MAX_VALUE;
    public static final long MISSING_VALUE = 0;
    public static final int DATA_COUNT = 100_000;

    private Long2LongZbMap map;

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
        if (map != null)
        {
            map.close();
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

    @Test
    public void shouldIncreaseHashTable()
    {
        // given
        final ZbMap<LongKeyHandler, LongValueHandler> zbMap =
            new ZbMap<LongKeyHandler, LongValueHandler>(4, 2, SIZE_OF_LONG, SIZE_OF_LONG) { };
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
    public void shouldPutNextPowerOfTwoForOddtableSize()
    {
        // given map not power of two
        final int tableSize = 3;

        // when
        final ZbMap<LongKeyHandler, LongValueHandler> zbMap =
            new ZbMap<LongKeyHandler, LongValueHandler>(tableSize, 2, SIZE_OF_LONG, SIZE_OF_LONG) { };

        // then map size is set to next power of two
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
        final ZbMap<LongKeyHandler, LongValueHandler> zbMap =
            new ZbMap<LongKeyHandler, LongValueHandler>(4, 1, SIZE_OF_LONG, SIZE_OF_LONG) { };

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

        // finally
        zbMap.close();
    }

    @Test
    public void shouldThrowExceptionIfTableSizeReachesDefaultMaxSize()
    {
        // given
        final ZbMap<EvenOddKeyHandler, LongValueHandler> zbMap =
            new ZbMap<EvenOddKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG) { };

        // expect
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("map Full. Cannot resize the hash table to size: " + (1L << 28) +
                                            ", reached max table size of " + ZbMap.MAX_TABLE_SIZE);

        // when
        for (int i = 0; i < DATA_COUNT; i++)
        {
            putValue(zbMap, i, i);
        }

        zbMap.close();
    }

    @Test
    public void shouldThrowExceptionIfTableSizeReachesMaxSize()
    {
        // given
        final ZbMap<EvenOddKeyHandler, LongValueHandler> zbMap =
            new ZbMap<EvenOddKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG) { };
        zbMap.setMaxTableSize(4);
        for (int i = 0; i < 2; i++)
        {
            putValue(zbMap, i, i);
        }

        // expect
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("map Full. Cannot resize the hash table to size: " + 8 +
                                            ", reached max table size of " + 4);

        // when
        putValue(zbMap, 3, 3);

        // finally
        zbMap.close();
    }

    @Test
    public void shouldUseNextPowerOfTwoForMaxSize()
    {
        // given
        final ZbMap<EvenOddKeyHandler, LongValueHandler> zbMap =
            new ZbMap<EvenOddKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG) { };

        // when
        zbMap.setMaxTableSize(3);

        // then
        assertThat(zbMap.maxTableSize).isEqualTo(4);

        // finally
        zbMap.close();
    }

    @Test
    public void shouldUseDefaultMaxTableSizeIfGivenSizeIsToLarge()
    {
        // given
        final ZbMap<EvenOddKeyHandler, LongValueHandler> zbMap =
            new ZbMap<EvenOddKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG) { };

        // when
        zbMap.setMaxTableSize(1 << 28);

        // then
        assertThat(zbMap.maxTableSize).isEqualTo(ZbMap.MAX_TABLE_SIZE);

        // finally
        zbMap.close();
    }

    @Test
    public void shouldOnlyUpdateEntry()
    {
        // given
        final ZbMap<LongKeyHandler, LongValueHandler> zbMap =
            new ZbMap<LongKeyHandler, LongValueHandler>(2, 1, SIZE_OF_LONG, SIZE_OF_LONG) { };

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
        // given map not power of two
        final int tableSize = 11;

        // when
        map = new Long2LongZbMap(tableSize, 1);

        // then map size is set to next power of two
        assertThat(map.tableSize).isEqualTo(16);

        // and a value can be inserted and read again
        map.put(KEY, VALUE);
        assertThat(map.get(KEY, MISSING_VALUE)).isEqualTo(VALUE);
    }

    @Test
    public void shouldUseLimitPowerOfTwo()
    {
        // given map which is higher than the limit 1 << 27
        final int tableSize = 1 << 28;

        // when
        map = new Long2LongZbMap(tableSize, 1);

        // then map size is set to max value
        assertThat(map.tableSize).isEqualTo(ZbMap.MAX_TABLE_SIZE);

        // and a value can be inserted and read again
        map.put(KEY, VALUE);
        assertThat(map.get(KEY, MISSING_VALUE)).isEqualTo(VALUE);
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
            getBlockLength(Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2)
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
                new Long2LongZbMap(1, Integer.MAX_VALUE / 20)
        )
                .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
                new Long2BytesZbMap(1, Integer.MAX_VALUE, 8)
        )
                .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
                new Long2BytesZbMap(1, Integer.MAX_VALUE / 20, 8)
        )
                .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
                new Bytes2LongZbMap(1, Integer.MAX_VALUE, 8)
        )
                .isInstanceOf(ArithmeticException.class);

        assertThatThrownBy(() ->
                new Bytes2LongZbMap(1, Integer.MAX_VALUE / 20, 8)
        )
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    public void shouldThrowOnLengthOverflowOnInitialAllocation()
    {
        assertThatThrownBy(() ->
            new Long2LongZbMap(1, maxRecordPerBlockForLong2Longmap() + 1)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to allocate map data buffer")
            .hasCauseInstanceOf(ArithmeticException.class);
    }

    private int maxRecordPerBlockForLong2Longmap()
    {
        return (Integer.MAX_VALUE - BUCKET_DATA_OFFSET - BUCKET_DATA_OFFSET * ALLOCATION_FACTOR) / (getBlockLength(SIZE_OF_LONG, SIZE_OF_LONG) * ALLOCATION_FACTOR);
    }

}

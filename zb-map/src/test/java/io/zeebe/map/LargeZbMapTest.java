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

import static io.zeebe.map.ZbMapTest.getValue;
import static io.zeebe.map.ZbMapTest.putValue;
import static io.zeebe.map.ZbMapTest.removeValue;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import io.zeebe.map.types.LongKeyHandler;
import io.zeebe.map.types.LongValueHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class LargeZbMapTest
{

    public static final int DATA_SET_SIZE = 5_000_000;

    private ZbMap<LongKeyHandler, LongValueHandler> zbMap;

    @Before
    public void setUp()
    {
        zbMap = new ZbMap<LongKeyHandler, LongValueHandler>(8, 4, SIZE_OF_LONG, SIZE_OF_LONG)
        { };
    }

    @After
    public void tearDown()
    {
        zbMap.close();
    }

    @Test
    public void shouldPutAndRemoveLargeSetOfRandomValues()
    {
        // given
        final Random random = new Random(100);

        final Set<Long> values = new HashSet<>();
        while (values.size() < DATA_SET_SIZE)
        {
            final long i = Math.abs(random.nextLong());

            putValue(zbMap, i, i);

            values.add(i);
        }

        // when
        int removedValues = 0;
        for (Long value : values)
        {
            final boolean removed = removeValue(zbMap, value);

            if (removed)
            {
                removedValues += 1;
            }
        }

        // then
        // block count is equal to the missing values
        assertThat(removedValues).isEqualTo(values.size());

        // hash table was shrinked and bucket buffers are released
        assertThat(zbMap.getHashTable().getCapacity()).isEqualTo(8);
        assertThat(zbMap.getBucketBufferArray().getBucketBufferCount()).isEqualTo(1);
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(1);
        assertThat(zbMap.getBucketBufferArray().getBlockCount()).isEqualTo(0);
        assertThat(zbMap.getBucketBufferArray().realAddresses.length).isEqualTo(32);
    }

    @Test
    public void shouldPutAndRemoveLargeSetOfValues()
    {
        // given
        for (int i = 0; i < DATA_SET_SIZE; i++)
        {
            putValue(zbMap, i, i);
        }

        // when
        int removedValues = 0;
        for (int i = 0; i < DATA_SET_SIZE; i++)
        {

            final boolean removed = removeValue(zbMap, i);
            if (removed)
            {
                removedValues += 1;
            }
        }

        // then
        // block count is equal to the missing values
        assertThat(removedValues).isEqualTo(DATA_SET_SIZE);

        // hash table was shrinked and bucket buffers are released
        assertThat(zbMap.getHashTable().getCapacity()).isEqualTo(8);
        assertThat(zbMap.getBucketBufferArray().getBucketBufferCount()).isEqualTo(1);
        assertThat(zbMap.getBucketBufferArray().getBucketCount()).isEqualTo(1);
        assertThat(zbMap.getBucketBufferArray().getBlockCount()).isEqualTo(0);
        assertThat(zbMap.getBucketBufferArray().realAddresses.length).isEqualTo(32);
    }

    @Test
    public void shouldPutAndGetElements()
    {
        // given
        for (int i = 0; i < DATA_SET_SIZE; i++)
        {
            putValue(zbMap, i, i);
        }

        // when then
        for (int i = 0; i < DATA_SET_SIZE; i++)
        {
            if (getValue(zbMap, i, -1) != i)
            {
                throw new RuntimeException("Illegal value for " + i);
            }
        }
    }

    @Test
    public void shouldPutRandomElements()
    {
        // given
        final long[] keys = new long[DATA_SET_SIZE];
        final Random random = new Random();
        for (int k = 0; k < keys.length; k++)
        {
            keys[k] = Math.abs(random.nextLong());
        }

        // when
        for (int i = 0; i < DATA_SET_SIZE; i++)
        {
            putValue(zbMap, keys[i], i);
        }

        // then
        for (int i = 0; i < DATA_SET_SIZE; i++)
        {
            if (getValue(zbMap, keys[i], -1) != i)
            {
                throw new RuntimeException("Illegal value for key: " + keys[i] + " index: " + i);
            }
        }
    }
}

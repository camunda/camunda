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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import io.zeebe.map.iterator.Bytes2LongZbMapEntry;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.*;

public class Bytes2LongZbMapMinimalBlockSizeTest
{
    static final long MISSING_VALUE = -2;

    byte[][] keys = new byte[16][64];

    Bytes2LongZbMap map;

    @Before
    public void createmap()
    {
        final int tableSize = 32;

        map = new Bytes2LongZbMap(tableSize, 1, 64);

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
        map.close();
    }

    @Test
    public void shouldReturnMissingValueForEmptyMap()
    {
        // given that the map is empty
        assertThat(map.get(keys[0], MISSING_VALUE) == MISSING_VALUE);
    }

    @Test
    public void shouldReturnMissingValueForNonExistingKey()
    {
        // given
        map.put(keys[1], 1);

        // then
        assertThat(map.get(keys[0], MISSING_VALUE) == MISSING_VALUE);
    }

    @Test
    public void shouldReturnLongValueForKey()
    {
        // given
        map.put(keys[1], 1);

        // if then
        assertThat(map.get(keys[1], MISSING_VALUE)).isEqualTo(1);
    }

    @Test
    public void shouldReturnLongValueForKeyFromBuffer()
    {
        // given
        map.put(keys[1], 1);

        // if then
        final UnsafeBuffer keyBuffer = new UnsafeBuffer(keys[1]);
        assertThat(map.get(keyBuffer, 0, keyBuffer.capacity(), MISSING_VALUE)).isEqualTo(1);
    }

    @Test
    public void shouldSplit()
    {
        // given
        map.put(keys[0], 0);

        // if
        map.put(keys[1], 1);

        // then
        assertThat(map.bucketCount()).isEqualTo(2);
        assertThat(map.get(keys[0], MISSING_VALUE)).isEqualTo(0);
        assertThat(map.get(keys[1], MISSING_VALUE)).isEqualTo(1);
    }


    @Test
    public void shouldPutMultipleValues()
    {
        for (int i = 0; i < 16; i += 2)
        {
            map.put(keys[i], i);
        }

        for (int i = 1; i < 16; i += 2)
        {
            map.put(keys[i], i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(map.get(keys[i], MISSING_VALUE) == i);
        }
    }

    @Test
    public void shouldPutValueFromBuffer()
    {
        // when
        final UnsafeBuffer keyBuffer = new UnsafeBuffer(keys[1]);
        map.put(keyBuffer, 0, keyBuffer.capacity(), 1);

        // then
        assertThat(map.get(keys[1], -1L)).isEqualTo(1L);
    }

    @Test
    public void shouldPutMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            map.put(keys[i], i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(map.get(keys[i], MISSING_VALUE) == i);
        }

        assertThat(map.bucketCount()).isEqualTo(16);
    }

    @Test
    public void shouldReplaceMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            map.put(keys[i], i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(map.put(keys[i], i)).isTrue();
        }

        assertThat(map.bucketCount()).isEqualTo(16);
    }


    @Test
    public void shouldRemoveValueForKey()
    {
        // given
        map.put(keys[1], 1);

        // if
        final long removeResult = map.remove(keys[1], -1);

        //then
        assertThat(removeResult).isEqualTo(1);
        assertThat(map.get(keys[1], -1)).isEqualTo(-1);
    }

    @Test
    public void shouldRemoveValueForKeyFromBuffer()
    {
        // given
        map.put(keys[1], 1);

        // if
        final UnsafeBuffer keyBuffer = new UnsafeBuffer(keys[1]);
        final long removeResult = map.remove(keyBuffer, 0, keyBuffer.capacity(), -1);

        //then
        assertThat(removeResult).isEqualTo(1);
        assertThat(map.get(keys[1], -1)).isEqualTo(-1);
    }

    @Test
    public void shouldNotRemoveValueForDifferentKey()
    {
        // given
        map.put(keys[1], 1);
        map.put(keys[2], 2);

        // if
        final long removeResult = map.remove(keys[1], -1);

        //then
        assertThat(removeResult).isEqualTo(1);
        assertThat(map.get(keys[1], -1)).isEqualTo(-1);
        assertThat(map.get(keys[2], -1)).isEqualTo(2);
    }

    @Test
    public void shouldIterateOverMap()
    {
        // given
        for (int i = 0; i < 16; i++)
        {
            map.put(keys[i], i);
        }

        // if then
        final List<byte[]> foundKeys = new ArrayList<>();

        final Iterator<Bytes2LongZbMapEntry> iterator = map.iterator();
        final byte key[] = new byte[64];
        while (iterator.hasNext())
        {
            final Bytes2LongZbMapEntry entry = iterator.next();

            entry.getKey().getBytes(0, key);
            foundKeys.add(key.clone());
        }

        assertThat(foundKeys)
            .hasSameSizeAs(keys)
            .contains(keys);
    }

}
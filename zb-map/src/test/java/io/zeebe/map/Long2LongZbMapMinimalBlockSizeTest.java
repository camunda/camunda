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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class Long2LongZbMapMinimalBlockSizeTest
{
    static final long MISSING_VALUE = -2;

    Long2LongZbMap map;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void createmap() throws Exception
    {
        map = new Long2LongZbMap(16, 1);
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
        assertThat(map.get(0, MISSING_VALUE) == MISSING_VALUE);
    }

    @Test
    public void shouldReturnMissingValueForNonExistingKey()
    {
        // given
        map.put(1, 1);

        // then
        assertThat(map.get(0, MISSING_VALUE) == MISSING_VALUE);
    }

    @Test
    public void shouldReturnLongValueForKey()
    {
        // given
        map.put(1, 1);

        // if then
        assertThat(map.get(1, MISSING_VALUE)).isEqualTo(1);
    }

    @Test
    public void shouldRemoveValueForKey()
    {
        // given
        map.put(1, 1);

        // if
        final long removeResult = map.remove(1, -1);

        //then
        assertThat(removeResult).isEqualTo(1);
        assertThat(map.get(1, -1)).isEqualTo(-1);
    }

    @Test
    public void shouldNotRemoveValueForDifferentKey()
    {
        // given
        map.put(1, 1);
        map.put(2, 2);

        // if
        final long removeResult = map.remove(1, -1);

        //then
        assertThat(removeResult).isEqualTo(1);
        assertThat(map.get(1, -1)).isEqualTo(-1);
        assertThat(map.get(2, -1)).isEqualTo(2);
    }

    @Test
    public void shouldSplit()
    {
        // given
        map.put(0, 0);

        // if
        map.put(1, 1);

        // then
        assertThat(map.bucketCount()).isEqualTo(2);
        assertThat(map.get(0, MISSING_VALUE)).isEqualTo(0);
        assertThat(map.get(1, MISSING_VALUE)).isEqualTo(1);
    }

    @Test
    public void shouldSplitTwoTimes()
    {
        // given
        map.put(1, 1);
        assertThat(map.bucketCount()).isEqualTo(1);

        // if
        map.put(3, 3);

        // then
        assertThat(map.bucketCount()).isEqualTo(3);
        assertThat(map.get(1, MISSING_VALUE)).isEqualTo(1);
        assertThat(map.get(3, MISSING_VALUE)).isEqualTo(3);
    }

    @Test
    public void shouldPutMultipleValues()
    {
        for (int i = 0; i < 16; i += 2)
        {
            map.put(i, i);
        }

        for (int i = 1; i < 16; i += 2)
        {
            map.put(i, i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(map.get(i, MISSING_VALUE)).isEqualTo(i);
        }

        assertThat(map.bucketCount()).isEqualTo(16);
    }

    @Test
    public void shouldPutMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            map.put(i, i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(map.get(i, MISSING_VALUE)).isEqualTo(i);
        }

        assertThat(map.bucketCount()).isEqualTo(16);
    }

    @Test
    public void shouldReplaceMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            map.put(i, i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(map.put(i, i)).isTrue();
        }

        assertThat(map.bucketCount()).isEqualTo(16);
    }
    @Test
    public void shouldUseOverflowOnCollision()
    {
        // given
        map.setMaxTableSize(16);
        map.put(0L, 1L);

        // when
        map.put(16L, 2L);

        // then
        // hash collision with 0L hashes to the same block. Block size = 1 => overflow
        assertThat(map.get(0, -1)).isEqualTo(1);
        assertThat(map.get(16, -1)).isEqualTo(2);
    }

    @Test
    public void shouldThrowExceptionOnCollision()
    {
        map.setMaxTableSize(16);
        for (int i = 0; i < 16; i++)
        {
            map.put(i, i);
        }

        expectedException.expect(RuntimeException.class);

        // hash collision with 0L hashes to the same block. Block size = 1 => exception expected.
        // resize not possible
        map.put(16L, 2L);
    }

    @Test
    public void shouldResizeOnCollision()
    {
        // given
        for (int i = 0; i < 16; i++)
        {
            map.put(i, i);
        }

        // when
        map.put(16L, 2L);

        // then resize
        assertThat(map.tableSize).isEqualTo(32);
        for (int i = 0; i < 16; i++)
        {
            assertThat(map.get(i, -1)).isEqualTo(i);
        }
        assertThat(map.get(16L, -1)).isEqualTo(2L);
    }
}
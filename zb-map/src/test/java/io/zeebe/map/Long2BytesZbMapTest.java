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

import static org.agrona.BitUtil.SIZE_OF_BYTE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.*;
import org.junit.rules.ExpectedException;

public class Long2BytesZbMapTest
{
    protected static final int TABLE_SIZE = 16;
    protected static final int VALUE_LENGTH = 3 * SIZE_OF_BYTE;

    private static final byte[] VALUE = "bar".getBytes();
    private static final byte[] ANOTHER_VALUE = "plo".getBytes();

    private Long2BytesZbMap map;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void createmap()
    {
        map = new Long2BytesZbMap(TABLE_SIZE, 1, VALUE_LENGTH);
    }

    @After
    public void close()
    {
        map.close();
    }


    @Test
    public void shouldReturnFalseForEmptyMap()
    {
        // given that the map is empty
        assertThat(map.get(0, new byte[VALUE_LENGTH])).isFalse();
    }

    @Test
    public void shouldReturnFalseForNonExistingKey()
    {
        // given
        map.put(1, VALUE);

        // then
        assertThat(map.get(0, new byte[VALUE_LENGTH])).isFalse();
    }

    @Test
    public void shouldReturnValueForKey()
    {
        // given
        map.put(1, VALUE);

        // if then
        final byte[] value = new byte[VALUE_LENGTH];
        assertThat(map.get(1, value)).isTrue();
        assertThat(value).isEqualTo(VALUE);
    }

    @Test
    public void shouldRemoveValueForKey()
    {
        // given
        map.put(1, VALUE);

        // if then
        final byte[] value = new byte[VALUE_LENGTH];
        assertThat(map.remove(1, value)).isTrue();
        assertThat(value).isEqualTo(VALUE);

        assertThat(map.get(1, value)).isFalse();
    }

    @Test
    public void shouldNotRemoveValueForDifferentKey()
    {
        // given
        map.put(1, VALUE);
        map.put(2, ANOTHER_VALUE);

        // if
        final byte[] value = new byte[VALUE_LENGTH];
        assertThat(map.remove(1, value)).isTrue();
        assertThat(value).isEqualTo(VALUE);

        //then
        assertThat(map.get(1, value)).isFalse();

        assertThat(map.get(2, value)).isTrue();
        assertThat(value).isEqualTo(ANOTHER_VALUE);
    }

    @Test
    public void shouldNotRemoveValueForNonExistingKey()
    {
        // given
        map.put(1, VALUE);

        // if
        final byte[] value = new byte[VALUE_LENGTH];
        assertThat(map.remove(0, value)).isFalse();

        //then
        assertThat(map.get(1, value)).isTrue();
        assertThat(value).isEqualTo(VALUE);
    }

    @Test
    public void shouldSplit()
    {
        // given
        map.put(0, VALUE);

        // if
        map.put(1, ANOTHER_VALUE);

        // then
        assertThat(map.bucketCount()).isEqualTo(2);

        final byte[] value = new byte[VALUE_LENGTH];
        assertThat(map.get(0, value)).isTrue();
        assertThat(value).isEqualTo(VALUE);

        assertThat(map.get(1, value)).isTrue();
        assertThat(value).isEqualTo(ANOTHER_VALUE);
    }

    @Test
    public void shouldSplitTwoTimes()
    {
        // given
        map.put(1, VALUE);
        assertThat(map.bucketCount()).isEqualTo(1);

        // if
        map.put(3, ANOTHER_VALUE);

        // then
        assertThat(map.bucketCount()).isEqualTo(3);

        final byte[] value = new byte[VALUE_LENGTH];
        assertThat(map.get(1, value)).isTrue();
        assertThat(value).isEqualTo(VALUE);

        assertThat(map.get(3, value)).isTrue();
        assertThat(value).isEqualTo(ANOTHER_VALUE);
    }

    @Test
    public void shouldPutMultipleValues()
    {
        for (int i = 0; i < 16; i += 2)
        {
            map.put(i, VALUE);
        }

        for (int i = 1; i < 16; i += 2)
        {
            map.put(i, ANOTHER_VALUE);
        }

        final byte[] value = new byte[VALUE_LENGTH];
        for (int i = 0; i < 16; i++)
        {
            assertThat(map.get(i, value)).isTrue();
            assertThat(value).isEqualTo(i % 2 == 0 ? VALUE : ANOTHER_VALUE);
        }

        assertThat(map.bucketCount()).isEqualTo(16);
    }

    @Test
    public void shouldPutMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            map.put(i, i < 8 ? VALUE : ANOTHER_VALUE);
        }

        final byte[] value = new byte[VALUE_LENGTH];
        for (int i = 0; i < 16; i++)
        {
            assertThat(map.get(i, value)).isTrue();
            assertThat(value).isEqualTo(i < 8 ? VALUE : ANOTHER_VALUE);
        }

        assertThat(map.bucketCount()).isEqualTo(16);
    }

    @Test
    public void shouldReplaceMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            map.put(i, VALUE);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(map.put(i, ANOTHER_VALUE)).isTrue();
        }

        assertThat(map.bucketCount()).isEqualTo(16);
    }

    @Test
    public void cannotPutValueIfmapFull()
    {
        // given
        map.setMaxTableSize(16);
        map.put(0, VALUE);

        thrown.expect(RuntimeException.class);

        map.put(16, ANOTHER_VALUE);
    }

    @Test
    public void shouldResizeOnCollision()
    {
        // given
        map.put(0L, VALUE);

        // when
        map.put(16L, ANOTHER_VALUE);

        // then resize
        assertThat(map.tableSize).isEqualTo(32);

        final byte[] value = new byte[VALUE_LENGTH];
        assertThat(map.get(0L, value)).isTrue();
        assertThat(value).isEqualTo(VALUE);
        assertThat(map.get(16L, value)).isTrue();
        assertThat(value).isEqualTo(ANOTHER_VALUE);
    }

    @Test
    public void cannotPutValueIfValueIsTooLong()
    {
        thrown.expect(IllegalArgumentException.class);

        map.put(0, "too long".getBytes());
    }

    @Test
    public void shouldClear()
    {
        // given
        map.put(0, VALUE);

        // when
        map.clear();

        // then
        assertThat(map.get(0, new byte[VALUE_LENGTH])).isFalse();
    }

    @Test
    public void shouldNotOverwriteValue()
    {
        // given
        final byte[] originalAnotherValue = ANOTHER_VALUE.clone();
        map.put(0, VALUE);
        map.put(1, ANOTHER_VALUE);

        // when
        final byte[] value = new byte[VALUE_LENGTH];
        map.get(0, value);

        // then
        assertThat(ANOTHER_VALUE).isEqualTo(originalAnotherValue);
    }

}

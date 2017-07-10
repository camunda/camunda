/**
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

import static org.agrona.BitUtil.SIZE_OF_BYTE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.*;
import org.junit.rules.ExpectedException;

public class Long2BytesHashIndexTest
{
    protected static final int INDEX_SIZE = 16;
    protected static final int VALUE_LENGTH = 3 * SIZE_OF_BYTE;

    private static final byte[] VALUE = "bar".getBytes();
    private static final byte[] ANOTHER_VALUE = "plo".getBytes();

    private Long2BytesHashIndex index;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void createIndex()
    {
        index = new Long2BytesHashIndex(INDEX_SIZE, 1, VALUE_LENGTH);
    }

    @After
    public void close()
    {
        index.close();
    }


    @Test
    public void shouldReturnFalseForEmptyMap()
    {
        // given that the map is empty
        assertThat(index.get(0, new byte[VALUE_LENGTH])).isFalse();
    }

    @Test
    public void shouldReturnFalseForNonExistingKey()
    {
        // given
        index.put(1, VALUE);

        // then
        assertThat(index.get(0, new byte[VALUE_LENGTH])).isFalse();
    }

    @Test
    public void shouldReturnValueForKey()
    {
        // given
        index.put(1, VALUE);

        // if then
        final byte[] value = new byte[VALUE_LENGTH];
        assertThat(index.get(1, value)).isTrue();
        assertThat(value).isEqualTo(VALUE);
    }

    @Test
    public void shouldRemoveValueForKey()
    {
        // given
        index.put(1, VALUE);

        // if then
        final byte[] value = new byte[VALUE_LENGTH];
        assertThat(index.remove(1, value)).isTrue();
        assertThat(value).isEqualTo(VALUE);

        assertThat(index.get(1, value)).isFalse();
    }

    @Test
    public void shouldNotRemoveValueForDifferentKey()
    {
        // given
        index.put(1, VALUE);
        index.put(2, ANOTHER_VALUE);

        // if
        final byte[] value = new byte[VALUE_LENGTH];
        assertThat(index.remove(1, value)).isTrue();
        assertThat(value).isEqualTo(VALUE);

        //then
        assertThat(index.get(1, value)).isFalse();

        assertThat(index.get(2, value)).isTrue();
        assertThat(value).isEqualTo(ANOTHER_VALUE);
    }

    @Test
    public void shouldNotRemoveValueForNonExistingKey()
    {
        // given
        index.put(1, VALUE);

        // if
        final byte[] value = new byte[VALUE_LENGTH];
        assertThat(index.remove(0, value)).isFalse();

        //then
        assertThat(index.get(1, value)).isTrue();
        assertThat(value).isEqualTo(VALUE);
    }

    @Test
    public void shouldSplit()
    {
        // given
        index.put(0, VALUE);

        // if
        index.put(1, ANOTHER_VALUE);

        // then
        assertThat(index.blockCount()).isEqualTo(2);

        final byte[] value = new byte[VALUE_LENGTH];
        assertThat(index.get(0, value)).isTrue();
        assertThat(value).isEqualTo(VALUE);

        assertThat(index.get(1, value)).isTrue();
        assertThat(value).isEqualTo(ANOTHER_VALUE);
    }

    @Test
    public void shouldSplitTwoTimes()
    {
        // given
        index.put(1, VALUE);
        assertThat(index.blockCount()).isEqualTo(1);

        // if
        index.put(3, ANOTHER_VALUE);

        // then
        assertThat(index.blockCount()).isEqualTo(3);

        final byte[] value = new byte[VALUE_LENGTH];
        assertThat(index.get(1, value)).isTrue();
        assertThat(value).isEqualTo(VALUE);

        assertThat(index.get(3, value)).isTrue();
        assertThat(value).isEqualTo(ANOTHER_VALUE);
    }

    @Test
    public void shouldPutMultipleValues()
    {
        for (int i = 0; i < 16; i += 2)
        {
            index.put(i, VALUE);
        }

        for (int i = 1; i < 16; i += 2)
        {
            index.put(i, ANOTHER_VALUE);
        }

        final byte[] value = new byte[VALUE_LENGTH];
        for (int i = 0; i < 16; i++)
        {
            assertThat(index.get(i, value)).isTrue();
            assertThat(value).isEqualTo(i % 2 == 0 ? VALUE : ANOTHER_VALUE);
        }

        assertThat(index.blockCount()).isEqualTo(16);
    }

    @Test
    public void shouldPutMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            index.put(i, i < 8 ? VALUE : ANOTHER_VALUE);
        }

        final byte[] value = new byte[VALUE_LENGTH];
        for (int i = 0; i < 16; i++)
        {
            assertThat(index.get(i, value)).isTrue();
            assertThat(value).isEqualTo(i < 8 ? VALUE : ANOTHER_VALUE);
        }

        assertThat(index.blockCount()).isEqualTo(16);
    }

    @Test
    public void shouldReplaceMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            index.put(i, VALUE);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(index.put(i, ANOTHER_VALUE)).isTrue();
        }

        assertThat(index.blockCount()).isEqualTo(16);
    }

    @Test
    public void cannotPutValueIfIndexFull()
    {
        // given
        index.put(0, VALUE);

        thrown.expect(RuntimeException.class);

        index.put(16, ANOTHER_VALUE);
    }

    @Test
    public void cannotPutValueIfValueIsTooLong()
    {
        thrown.expect(IllegalArgumentException.class);

        index.put(0, "too long".getBytes());
    }

    @Test
    public void shouldClear()
    {
        // given
        index.put(0, VALUE);

        // when
        index.clear();

        // then
        assertThat(index.get(0, new byte[VALUE_LENGTH])).isFalse();
    }

    @Test
    public void shouldNotOverwriteValue()
    {
        // given
        final byte[] originalAnotherValue = ANOTHER_VALUE.clone();
        index.put(0, VALUE);
        index.put(1, ANOTHER_VALUE);

        // when
        final byte[] value = new byte[VALUE_LENGTH];
        index.get(0, value);

        // then
        assertThat(ANOTHER_VALUE).isEqualTo(originalAnotherValue);
    }

}

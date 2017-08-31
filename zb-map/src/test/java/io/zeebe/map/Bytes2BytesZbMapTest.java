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

import java.util.Arrays;
import java.util.Random;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class Bytes2BytesZbMapTest
{

    byte[] key = new byte[64];
    byte[] value = new byte[64];

    UnsafeBuffer keyBuffer;
    UnsafeBuffer valueBuffer;

    Bytes2BytesZbMap map;

    @Rule
    public ExpectedException expection = ExpectedException.none();

    @Before
    public void createmap()
    {
        final int tableSize = 32;

        map = new Bytes2BytesZbMap(tableSize, 1, 64, 64);

        final Random r = new Random();
        r.nextBytes(key);
        r.nextBytes(value);

        keyBuffer = new UnsafeBuffer(key);
        valueBuffer = new UnsafeBuffer(value);
    }

    @After
    public void close()
    {
        map.close();
    }

    @Test
    public void shouldGetValue()
    {
        // given
        map.put(key, value);
        final byte[] result = new byte[64];

        // when
        final boolean found = map.get(key, result);

        // then
        assertThat(found).isTrue();
        assertThat(result).isEqualTo(value);
    }


    @Test
    public void shouldNotGetMissingValue()
    {
        // given
        final byte[] result = Arrays.copyOf(value, value.length);

        // when
        final boolean found = map.get(key, result);

        // then
        assertThat(found).isFalse();
        assertThat(result).isEqualTo(value);
    }

    @Test
    public void shouldRemoveValue()
    {
        // given
        map.put(key, value);

        // when
        final boolean removed = map.remove(key);

        // then
        assertThat(removed).isTrue();
        assertThat(map.get(key, value)).isFalse();
    }

    @Test
    public void shouldGetValueAsBuffer()
    {
        // given
        map.put(keyBuffer, valueBuffer);
        final UnsafeBuffer result = new UnsafeBuffer(new byte[64]);

        // when
        final boolean found = map.get(keyBuffer, result);

        // then
        assertThat(found).isTrue();
        assertThat(result).isEqualTo(valueBuffer);
    }


    @Test
    public void shouldNotGetMissingValueAsBuffer()
    {
        // given
        final UnsafeBuffer result = new UnsafeBuffer(Arrays.copyOf(value, value.length));

        // when
        final boolean found = map.get(keyBuffer, result);

        // then
        assertThat(found).isFalse();
        assertThat(result).isEqualTo(valueBuffer);
    }

    @Test
    public void shouldRemoveValueAsBuffer()
    {
        // given
        map.put(keyBuffer, valueBuffer);

        // when
        final boolean removed = map.remove(keyBuffer);

        // then
        assertThat(removed).isTrue();
        assertThat(map.get(keyBuffer, valueBuffer)).isFalse();
    }


    @Test
    public void shouldFillShorterKeysWithZero()
    {
        // given
        final byte[] key = new byte[64];
        final byte[] shortenedKey = new byte[30];
        System.arraycopy(key, 0, key, 0, 30);
        System.arraycopy(key, 0, shortenedKey, 0, 30);

        map.put(key, value);

        final byte[] result = new byte[64];

        // when
        map.get(shortenedKey, result);

        // then
        assertThat(result).isEqualTo(value);
    }

    @Test
    public void shouldRejectIfKeyTooLong()
    {
        // then
        expection.expect(IllegalArgumentException.class);

        // when
        map.get(new byte[65], value);
    }


}

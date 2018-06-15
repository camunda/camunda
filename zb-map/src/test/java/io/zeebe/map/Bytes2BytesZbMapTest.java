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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.zeebe.map.iterator.Bytes2BytesZbMapEntry;

public class Bytes2BytesZbMapTest
{

    private static final int KEY_LENGTH = 64;
    private static final int VALUE_LENGTH = 64;

    UnsafeBuffer key = new UnsafeBuffer(new byte[64]);
    UnsafeBuffer value = new UnsafeBuffer(new byte[64]);

    Bytes2BytesZbMap map;

    @Rule
    public ExpectedException expection = ExpectedException.none();

    @Before
    public void createmap()
    {
        final int tableSize = 32;

        map = new Bytes2BytesZbMap(tableSize, 1, KEY_LENGTH, VALUE_LENGTH);

        final Random r = new Random();
        r.nextBytes(key.byteArray());
        r.nextBytes(value.byteArray());
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

        // when
        final DirectBuffer result = map.get(key);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(value);
    }


    @Test
    public void shouldNotGetMissingValue()
    {
        // when
        final DirectBuffer result = map.get(key);

        // then
        assertThat(result).isNull();
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
        assertThat(map.get(key)).isNull();
    }

    @Test
    public void shouldGetValueWithKeyOffset()
    {
        // given
        final int offset = 2;
        final UnsafeBuffer offsetKey = new UnsafeBuffer(new byte[key.capacity() + offset]);
        offsetKey.putBytes(offset, key, 0, key.capacity());
        map.put(key, value);

        // when
        final DirectBuffer result = map.get(offsetKey, offset, key.capacity());

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(value);
    }



    @Test
    public void shouldPutValueWithKeyOffset()
    {
        // given
        final int offset = 2;
        final UnsafeBuffer offsetKey = new UnsafeBuffer(new byte[key.capacity() + offset]);
        offsetKey.putBytes(offset, key, 0, key.capacity());
        map.put(offsetKey, offset, key.capacity(), value);

        // when
        final DirectBuffer result = map.get(key);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(value);
    }



    @Test
    public void shouldNotGetMissingValueAsBuffer()
    {
        // given
        final int offset = 2;
        final UnsafeBuffer offsetKey = new UnsafeBuffer(new byte[key.capacity() + offset]);

        // when
        final DirectBuffer result = map.get(offsetKey, offset, key.capacity());

        // then
        assertThat(result).isNull();
    }

    @Test
    public void shouldRemoveValueWithKeyOffset()
    {
        // given
        final int offset = 2;
        final UnsafeBuffer offsetKey = new UnsafeBuffer(new byte[key.capacity() + offset]);
        offsetKey.putBytes(offset, key, 0, key.capacity());
        map.put(key, value);

        // when
        final boolean removed = map.remove(offsetKey, offset, key.capacity());

        // then
        assertThat(removed).isTrue();
        assertThat(map.get(key)).isNull();
    }

    @Test
    public void shouldFillShorterKeysWithZero()
    {
        // given
        final UnsafeBuffer shortenedKey = new UnsafeBuffer(new byte[30]);
        shortenedKey.putBytes(0, key, 0, shortenedKey.capacity());

        map.put(shortenedKey, value);

        // when
        final DirectBuffer result = map.get(shortenedKey);

        // then
        assertThat(result).isEqualTo(value);
    }

    @Test
    public void shouldFailToGetValueWithHalfKey()
    {
        // given
        final UnsafeBuffer shortenedKey = new UnsafeBuffer(new byte[30]);
        shortenedKey.putBytes(0, key, 0, shortenedKey.capacity());

        map.put(key, value);

        // when
        final DirectBuffer result = map.get(shortenedKey);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void shouldRejectGetIfKeyTooLong()
    {
        // then
        expection.expect(IllegalArgumentException.class);

        // when
        map.get(new UnsafeBuffer(new byte[65]));
    }

    @Test
    public void shouldRejectPutIfKeyTooLong()
    {
        // then
        expection.expect(IllegalArgumentException.class);

        // when
        map.put(new UnsafeBuffer(new byte[65]), value);
    }

    @Test
    public void shouldRejectPutIfValueTooLong()
    {
        // then
        expection.expect(IllegalArgumentException.class);

        // when
        map.put(key, new UnsafeBuffer(new byte[65]));
    }

    @Test
    public void shouldRejectRemoveIfKeyTooLong()
    {
        // then
        expection.expect(IllegalArgumentException.class);

        // when
        map.remove(new UnsafeBuffer(new byte[65]));
    }

    @Test
    public void shouldIterateOverMap()
    {
        // given
        final Map<DirectBuffer, DirectBuffer> entries = new HashMap<>();

        for (int i = 0; i < 16; i++)
        {
            final byte[] key = new byte[KEY_LENGTH];
            final byte[] value = new byte[VALUE_LENGTH];

            key[0] = (byte) i;
            value[VALUE_LENGTH - 1] = (byte) i;

            entries.put(new UnsafeBuffer(key), new UnsafeBuffer(value));
            map.put(key, value);
        }

        // when then
        final Iterator<Bytes2BytesZbMapEntry> iterator = map.iterator();
        while (iterator.hasNext())
        {
            final Bytes2BytesZbMapEntry entry = iterator.next();

            final DirectBuffer key = entry.getKey();
            assertThat(entries).containsKey(key);

            final DirectBuffer expectedValue = entries.remove(key);
            assertThat(entry.getValue()).isEqualTo(expectedValue);
        }

        assertThat(entries).isEmpty();
    }

}

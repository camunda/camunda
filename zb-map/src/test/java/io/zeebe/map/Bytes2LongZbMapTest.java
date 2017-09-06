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

import org.junit.*;
import org.junit.rules.ExpectedException;

public class Bytes2LongZbMapTest
{

    byte[][] keys = new byte[16][64];

    Bytes2LongZbMap map;

    @Rule
    public ExpectedException expection = ExpectedException.none();

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
    public void shouldFillShorterKeysWithZero()
    {
        // given
        final byte[] key = new byte[64];
        final byte[] shortenedKey = new byte[30];
        System.arraycopy(keys[1], 0, key, 0, 30);
        System.arraycopy(keys[1], 0, shortenedKey, 0, 30);

        map.put(key, 76L);

        // when then
        assertThat(map.get(shortenedKey, -1)).isEqualTo(76L);
    }

    @Test
    public void shouldRejectGetIfKeyTooLong()
    {
        // then
        expection.expect(IllegalArgumentException.class);

        // when
        map.get(new byte[65], -2);
    }

    @Test
    public void shouldRejectPutIfKeyTooLong()
    {
        // then
        expection.expect(IllegalArgumentException.class);

        // when
        map.put(new byte[65], 2);
    }

    @Test
    public void shouldRejectRemoveIfKeyTooLong()
    {
        // then
        expection.expect(IllegalArgumentException.class);

        // when
        map.remove(new byte[65], -2);
    }

}

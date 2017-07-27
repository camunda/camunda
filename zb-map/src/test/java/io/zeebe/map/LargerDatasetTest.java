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

import java.io.IOException;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class LargerDatasetTest
{
    private static final int KEYS_TO_PUT = 1_000_000;

    private static final int BLOCKS_PER_BUCKET = 16;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Long2LongZbMap map;

    @Before
    public void setUp() throws IOException
    {
        map = new Long2LongZbMap(32, BLOCKS_PER_BUCKET);
    }

    @After
    public void after()
    {
        map.close();
    }

    @Test
    public void shouldPutElements()
    {
        for (int i = 0; i < KEYS_TO_PUT; i++)
        {
            map.put(i, i);
        }

        for (int i = 0; i < KEYS_TO_PUT; i++)
        {
            if (map.get(i, -1) != i)
            {
                throw new RuntimeException("Illegal value for " + i);
            }
        }
    }

    @Test
    public void shouldPutRandomElements()
    {
        // given
        final long[] keys = new long[KEYS_TO_PUT];
        final Random random = new Random();
        for (int k = 0; k < keys.length; k++)
        {
            keys[k] = Math.abs(random.nextLong());
        }

        for (int i = 0; i < KEYS_TO_PUT; i++)
        {
            map.put(keys[i], i);
        }

        for (int i = 0; i < KEYS_TO_PUT; i++)
        {
            if (map.get(keys[i], -1) != i)
            {
                throw new RuntimeException("Illegal value for key: " + keys[i] + " index: " + i);
            }
        }
    }
}

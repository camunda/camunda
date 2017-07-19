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

import org.agrona.BitUtil;
import org.junit.*;
import org.junit.rules.ExpectedException;

public class LargerDatasetTest
{
    private static final int KEYS_TO_PUT = 1_000_000;

    private static final int RECORDS_PER_BLOCK = 16;

    private static final int TABLE_SIZE = BitUtil.findNextPositivePowerOfTwo(KEYS_TO_PUT / RECORDS_PER_BLOCK);

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Long2LongZbMap map;

    @Before
    public void setUp() throws IOException
    {
        map = new Long2LongZbMap(TABLE_SIZE, RECORDS_PER_BLOCK);
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
}

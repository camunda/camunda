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
package io.zeebe.map.benchmarks;

import static org.agrona.BitUtil.SIZE_OF_LONG;

import java.util.Random;

import io.zeebe.map.Long2LongZbMap;
import io.zeebe.map.ZbMap;

public class ZbMapDetermineLoadFactorBenchmark
{
    private static final int KEYS_TO_PUT = 10_000_000;
    private static final int MAX_POWER = 10;
    private static final int DEFAULT_TABLE_SIZE = ZbMap.DEFAULT_TABLE_SIZE;

    public static void benchmarkOptimalFactorLinearKeys()
    {
        System.out.println("Generate: " + KEYS_TO_PUT + " keys");
        final long[] keys = new long[KEYS_TO_PUT];
        for (int k = 0; k < keys.length; k++)
        {
            keys[k] = (long) k;
        }

        System.out.println("Long2LongZbMap - linear keys");
        for (int power = 2; power < MAX_POWER; power++)
        {
            final int blocksPerBucket = 1 << power;
            System.out.println("\nKeys to put\t\ttableSize\t\tBlocks per bucket\t\tLoad factor\t\tEnd tableSize\t\tDuration in ms");
            for (int fac = 1; fac < 10; fac++)
            {
                try
                {
                    final double factor = (double) fac / (double) 10;
                    System.out.print("\n" + KEYS_TO_PUT + "\t\t\t" + DEFAULT_TABLE_SIZE + "\t\t\t\t\t\t" + blocksPerBucket + "\t\t\t\t" + factor);
                    final Long2LongZbMap map = new Long2LongZbMap(DEFAULT_TABLE_SIZE, blocksPerBucket);
                    map.setLoadFactorOverflowLimit(factor);

                    final long startMillis = System.currentTimeMillis();
                    for (int i = 0; i < KEYS_TO_PUT; i++)
                    {
                        map.put(keys[i], i);
                    }
                    System.out.print("\t\t\t\t" + map.getHashTableSize() / SIZE_OF_LONG);
                    for (int i = 0; i < KEYS_TO_PUT; i++)
                    {
                        if (map.get(keys[i], -1) != i)
                        {
                            throw new RuntimeException("Illegal value for " + i);
                        }
                    }
                    final long endMillis = System.currentTimeMillis();
                    System.out.print("\t\t\t" + (endMillis - startMillis));
                    map.close();
                }
                catch (Exception e)
                {
                    System.out.println("\nBlocks per bucket " + blocksPerBucket + " to small for " + KEYS_TO_PUT + " entries." + e.getMessage());
                }
            }
        }
    }

    public static void benchmarkOptimalFactorRandomKeys()
    {
        System.out.println("Generate: " + KEYS_TO_PUT + " keys");
        final long[] keys = new long[KEYS_TO_PUT];
        final Random random = new Random();
        for (int k = 0; k < keys.length; k++)
        {
            keys[k] = Math.abs(random.nextLong());
        }

        System.out.println("Long2LongZbMap - random keys");
        for (int power = 2; power < MAX_POWER; power++)
        {
            final int blocksPerBucket = 1 << power;
            System.out.println("\nKeys to put\t\ttableSize\t\tBlocks per bucket\t\tLoad factor\t\tEnd tableSize\t\tDuration in ms");
            for (int fac = 1; fac < 10; fac++)
            {
                try
                {
                    final double factor = (double) fac / (double) 10;
                    System.out.print("\n" + KEYS_TO_PUT + "\t\t\t" + DEFAULT_TABLE_SIZE + "\t\t\t\t\t\t" + blocksPerBucket + "\t\t\t\t" + factor);
                    final Long2LongZbMap map = new Long2LongZbMap(DEFAULT_TABLE_SIZE, blocksPerBucket);
                    map.setLoadFactorOverflowLimit(factor);

                    final long startMillis = System.currentTimeMillis();
                    for (int i = 0; i < KEYS_TO_PUT; i++)
                    {
                        map.put(keys[i], i);
                    }
                    System.out.print("\t\t\t\t" + map.getHashTableSize() / SIZE_OF_LONG);
                    for (int i = 0; i < KEYS_TO_PUT; i++)
                    {
                        if (map.get(keys[i], -1) != i)
                        {
                            throw new RuntimeException("Illegal value for " + i);
                        }
                    }
                    final long endMillis = System.currentTimeMillis();
                    System.out.print("\t\t\t" + (endMillis - startMillis));
                    map.close();
                }
                catch (Exception e)
                {
                    System.out.println("\nBlocks per bucket " + blocksPerBucket + " to small for " + KEYS_TO_PUT + " entries." + e.getMessage());
                }
            }
        }
    }

    public static void main(String[] args)
    {
        benchmarkOptimalFactorLinearKeys();
        benchmarkOptimalFactorRandomKeys();
    }
}

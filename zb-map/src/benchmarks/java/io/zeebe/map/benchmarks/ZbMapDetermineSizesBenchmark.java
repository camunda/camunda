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

import io.zeebe.map.*;

public class ZbMapDetermineSizesBenchmark
{
    private static final int KEYS_TO_PUT = 10_000_000;
    private static final int MAX_POWER = 10;
    private static final int DEFAULT_TABLE_SIZE = ZbMap.DEFAULT_TABLE_SIZE;

    public static void benchmarkOptimalValuesForLong2Long()
    {
        System.out.println("Long2LongZbMap");
        System.out.println("Keys to put\t\ttableSize\t\tBlocks per Bucket\t\tEnd tableSize\t\tDuration in ms");
        for (int power = 1; power < MAX_POWER; power++)
        {
            final int recordsPerBlock = 1 << power;
            final int tableSize = DEFAULT_TABLE_SIZE; //BitUtil.findNextPositivePowerOfTwo((int) Math.ceil((double) KEYS_TO_PUT / recordsPerBlock));
            System.out.print("\n" + KEYS_TO_PUT + "\t\t\t" + tableSize + "\t\t\t\t\t\t" + recordsPerBlock);
            final Long2LongZbMap map = new Long2LongZbMap(tableSize, recordsPerBlock);

            final long startMillis = System.currentTimeMillis();
            for (int i = 0; i < KEYS_TO_PUT; i++)
            {
                map.put(i, i);
            }
            System.out.print("\t\t\t\t" + map.getHashTableSize() / SIZE_OF_LONG);
            for (int i = 0; i < KEYS_TO_PUT; i++)
            {
                if (map.get(i, -1) != i)
                {
                    throw new RuntimeException("Illegal value for " + i);
                }
            }
            final long endMillis = System.currentTimeMillis();
            System.out.print("\t\t\t" + (endMillis - startMillis));
            map.close();
        }
    }

    public static void benchmarkOptimalValuesForBytes2Long()
    {
        System.out.println("Bytes2LongZbMap");
        System.out.println("Keys to put\t\tKeylength\t\ttableSize\t\tBlocks per Bucket\t\tEnd tableSize\t\tDuration in ms");
        for (int power = 1; power < MAX_POWER; power++)
        {
            final int recordsPerBlock = 1 << power;
            final int tableSize = DEFAULT_TABLE_SIZE; //BitUtil.findNextPositivePowerOfTwo((int) Math.ceil((double) KEYS_TO_PUT / recordsPerBlock));
            final int keyLength = 1024;
            System.out.print("\n" + KEYS_TO_PUT + "\t\t\t" + keyLength + "\t\t\t" + tableSize + "\t\t\t\t\t\t" + recordsPerBlock);
            final Bytes2LongZbMap map = new Bytes2LongZbMap(tableSize, recordsPerBlock, keyLength);

            final long startMillis = System.currentTimeMillis();
            for (int i = 0; i < KEYS_TO_PUT; i++)
            {
                final byte key[] = ("" + i).getBytes();
                map.put(key, i);
            }
            System.out.print("\t\t\t\t" + map.getHashTableSize() / SIZE_OF_LONG);
            for (int i = 0; i < KEYS_TO_PUT; i++)
            {
                final byte key[] = ("" + i).getBytes();
                if (map.get(key, -1) != i)
                {
                    throw new RuntimeException("Illegal value for " + i);
                }
            }
            final long endMillis = System.currentTimeMillis();
            System.out.print("\t\t\t" + (endMillis - startMillis));
            map.close();
        }
    }

    public static void benchmarkOptimalValuesForLong2Bytes()
    {
        System.out.println("Long2BytesZbMap");
        System.out.println("Keys to put\t\tvalueLength\t\ttableSize\t\tBlocks per Bucket\t\tEnd tableSize\t\tDuration in ms");
        for (int power = 1; power < MAX_POWER; power++)
        {
            final int recordsPerBlock = 1 << power;
            final int tableSize = DEFAULT_TABLE_SIZE; //BitUtil.findNextPositivePowerOfTwo((int) Math.ceil((double) KEYS_TO_PUT / recordsPerBlock));
            final int valueLength = 1024;
            System.out.print("\n" + KEYS_TO_PUT + "\t\t\t" + valueLength + "\t\t\t" + tableSize + "\t\t\t\t\t\t" + recordsPerBlock);
            final Long2BytesZbMap map = new Long2BytesZbMap(tableSize, recordsPerBlock, valueLength);

            final long startMillis = System.currentTimeMillis();
            for (int i = 0; i < KEYS_TO_PUT; i++)
            {
                final byte key[] = ("" + i).getBytes();
                map.put(i, key);
            }
            System.out.print("\t\t\t\t" + map.getHashTableSize() / SIZE_OF_LONG);

            for (int i = 0; i < KEYS_TO_PUT; i++)
            {
                final byte value[] = new byte[64];
                if (!map.get(i, value))
                {
                    throw new RuntimeException("Illegal value " + new String(value) + " for " + i);
                }
            }
            final long endMillis = System.currentTimeMillis();
            System.out.print("\t\t\t" + (endMillis - startMillis));
            map.close();
        }
    }

    public static void main(String[] args)
    {
        benchmarkOptimalValuesForLong2Long();
//        benchmarkOptimalValuesForLong2Bytes();
        // Does not work since currently there is no bucket overflow implemented
//        benchmarkOptimalValuesForBytes2Long();
    }
}

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
package io.zeebe.hashindex.benchmarks;

import io.zeebe.hashindex.types.ByteArrayKeyHandler;
import org.agrona.BitUtil;
import io.zeebe.hashindex.*;

public class HashIndexDetermineSizesBenchmark
{
    private static final int KEYS_TO_PUT = 10_000_000;
    private static final int MAX_POWER = 12;

    public static void benchmarkOptimalValuesForLong2Long()
    {
        System.out.println("Long2LongHashIndex");
        System.out.println("Keys to put\t\tIndexsize\t\tRecords per Block\t\tDuration in ms");
        for (int power = 1; power < MAX_POWER; power++)
        {
            final int recordsPerBlock = 1 << power;
            final int indexSize = BitUtil.findNextPositivePowerOfTwo((int) Math.ceil((double) KEYS_TO_PUT / recordsPerBlock));
            System.out.print("\n" + KEYS_TO_PUT + "\t\t\t" + indexSize + "\t\t\t\t\t\t" + recordsPerBlock);
            final Long2LongHashIndex index = new Long2LongHashIndex(indexSize, recordsPerBlock);

            final long startMillis = System.currentTimeMillis();
            for (int i = 0; i < KEYS_TO_PUT; i++)
            {
                index.put(i, i);
            }

            for (int i = 0; i < KEYS_TO_PUT; i++)
            {
                if (index.get(i, -1) != i)
                {
                    throw new RuntimeException("Illegal value for " + i);
                }
            }
            final long endMillis = System.currentTimeMillis();
            System.out.print("\t\t\t" + (endMillis - startMillis));
            index.close();
        }
    }

    public static void benchmarkOptimalValuesForBytes2Long()
    {
        System.out.println("Bytes2LongHashIndex");
        System.out.println("Keys to put\t\tKeylength\t\tIndexsize\t\tRecords per Block\t\tDuration in ms");
        for (int power = 1; power < MAX_POWER; power++)
        {
            final int recordsPerBlock = 1 << power;
            final int indexSize = BitUtil.findNextPositivePowerOfTwo((int) Math.ceil((double) KEYS_TO_PUT / recordsPerBlock));
            final int keyLength = 1024;
            System.out.print("\n" + KEYS_TO_PUT + "\t\t\t" + keyLength + "\t\t\t" + indexSize + "\t\t\t\t\t\t" + recordsPerBlock);
            final Bytes2LongHashIndex index = new Bytes2LongHashIndex(indexSize, recordsPerBlock, keyLength);

            final long startMillis = System.currentTimeMillis();
            for (int i = 0; i < KEYS_TO_PUT; i++)
            {
                final byte key[] = ("" + i).getBytes();
                index.put(key, i);
            }

            for (int i = 0; i < KEYS_TO_PUT; i++)
            {
                final byte key[] = ("" + i).getBytes();
                if (index.get(key, -1) != i)
                {
                    throw new RuntimeException("Illegal value for " + i);
                }
            }
            final long endMillis = System.currentTimeMillis();
            System.out.print("\t\t\t" + (endMillis - startMillis));
            index.close();
        }
    }

    public static void benchmarkOptimalValuesForLong2Bytes()
    {
        System.out.println("Long2BytesHashIndex");
        System.out.println("Keys to put\t\tvalueLength\t\tIndexsize\t\tRecords per Block\t\tDuration in ms");
        for (int power = 1; power < MAX_POWER; power++)
        {
            final int recordsPerBlock = 1 << power;
            final int indexSize = BitUtil.findNextPositivePowerOfTwo((int) Math.ceil((double) KEYS_TO_PUT / recordsPerBlock));
            final int valueLength = 1024;
            System.out.print("\n" + KEYS_TO_PUT + "\t\t\t" + valueLength + "\t\t\t" + indexSize + "\t\t\t\t\t\t" + recordsPerBlock);
            final Long2BytesHashIndex index = new Long2BytesHashIndex(indexSize, recordsPerBlock, valueLength);

            final long startMillis = System.currentTimeMillis();
            for (int i = 0; i < KEYS_TO_PUT; i++)
            {
                final byte key[] = ("" + i).getBytes();
                index.put(i, key);
            }

            for (int i = 0; i < KEYS_TO_PUT; i++)
            {
                final byte value[] = new byte[64];
                if (!index.get(i, value))
                {
                    throw new RuntimeException("Illegal value " + new String(value) + " for " + i );
                }
            }
            final long endMillis = System.currentTimeMillis();
            System.out.print("\t\t\t" + (endMillis - startMillis));
            index.close();
        }
    }

    public static void main(String[] args)
    {
        benchmarkOptimalValuesForLong2Long();
        benchmarkOptimalValuesForLong2Bytes();
        // Does not work since currently there is no bucket overflow implemented
//        benchmarkOptimalValuesForBytes2Long();
    }
}

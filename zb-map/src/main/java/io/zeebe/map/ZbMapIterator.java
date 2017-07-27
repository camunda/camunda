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

import java.util.*;

import io.zeebe.map.iterator.ZbMapEntry;

/**
 * A recyclable, allocation-free iterator for a ZbMap.
 *
 * <p>
 * The implementation is not thread-safe and does not allow concurrent
 * modifications.
 */
public class ZbMapIterator<K extends KeyHandler, V extends ValueHandler, E extends ZbMapEntry<K, V>> implements Iterator<E>
{
    private final ZbMap<K, V> map;
    private final BucketBufferArray bucketBufferArray;
    private final E entry;

    private final K keyHandler;
    private final V valueHandler;

    int modCount;

    int currentBucket;

    long currentBucketAddress;
    int currentBucketOffset;
    int currentBlock;

    boolean hasNext;

    public ZbMapIterator(ZbMap<K, V> map, E entry)
    {
        this.map = map;
        this.bucketBufferArray = map.bucketBufferArray;
        this.entry = entry;

        this.keyHandler = map.keyHandler;
        this.valueHandler = map.valueHandler;

        reset();
    }

    public void reset()
    {
        modCount = map.modCount;

        currentBucket = 0;

        currentBucketAddress = bucketBufferArray.getFirstBucketOffset();
        currentBucketOffset = bucketBufferArray.getFirstBlockOffset();
        currentBlock = 0;

        hasNext = bucketBufferArray.getBucketCount() > 0 && bucketBufferArray.getBucketFillCount(currentBucketAddress) > 0;
    }

    @Override
    public boolean hasNext()
    {
        if (modCount != map.modCount)
        {
            throw new ConcurrentModificationException("The map was modified after reset() was called.");
        }

        return hasNext;
    }

    @Override
    public E next()
    {
        if (!hasNext())
        {
            throw new NoSuchElementException();
        }

        // read block
        bucketBufferArray.readKey(keyHandler, currentBucketAddress, currentBucketOffset);
        bucketBufferArray.readValue(valueHandler, currentBucketAddress, currentBucketOffset);

        entry.read(keyHandler, valueHandler);

        // move to next available block
        currentBlock = currentBlock + 1;

        if (currentBlock < bucketBufferArray.getBucketFillCount(currentBucketAddress))
        {
            // next block in the current bucket
            currentBucketOffset += bucketBufferArray.getBlockLength();
        }
        else
        {
            // the current bucket contains no more blocks
            // go to the next bucket which contains blocks
            currentBlock = 0;

            do
            {
                currentBucket += 1;
                currentBucketAddress += bucketBufferArray.getMaxBucketLength();

            }
            while (currentBucket < bucketBufferArray.getBucketCount() && bucketBufferArray.getBucketFillCount(currentBucketAddress) == 0);

            hasNext = currentBucket < bucketBufferArray.getBucketCount();

            if (hasNext)
            {
                currentBucketOffset = bucketBufferArray.getFirstBlockOffset();
            }
        }

        return entry;
    }

}

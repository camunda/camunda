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

import static io.zeebe.map.BucketArray.ALLOCATION_FACTOR;
import static io.zeebe.map.ZbMapDescriptor.*;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import io.zeebe.map.types.ByteArrayValueHandler;
import io.zeebe.map.types.LongKeyHandler;
import io.zeebe.map.types.LongValueHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 *
 */
public class BucketArrayTest
{
    private static final int MAX_KEY_LEN = SIZE_OF_LONG;
    private static final int MAX_VALUE_LEN = SIZE_OF_LONG;
    private static final int MIN_BLOCK_COUNT = 2;

    protected BucketArray bucketArray;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void init()
    {
        bucketArray = new BucketArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
    }

    @After
    public void close()
    {
        bucketArray.close();
    }

    @Test
    public void shouldCreateBucketArray()
    {
        // given bucketArray
        assertThat(bucketArray.getBucketCount()).isEqualTo(0);
        assertThat(bucketArray.getOccupiedBlocks()).isEqualTo(0);
        assertThat(bucketArray.getLength()).isEqualTo(BUCKET_BUFFER_HEADER_LENGTH + ALLOCATION_FACTOR * bucketArray.getMaxBucketLength());
    }

    @Test
    public void shouldThrowOverflowExceptionForToLargeMinBlockCount()
    {
        // expect
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("Unable to allocate map data buffer");

        // when
        new BucketArray(1 << 54, SIZE_OF_LONG, SIZE_OF_LONG);
    }

    @Test
    public void shouldThrowOverflowExceptionForToLargeKeyLength()
    {
        // expect
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("Unable to allocate map data buffer");

        // when
        new BucketArray(8, 1 << 55, SIZE_OF_LONG);
    }

    @Test
    public void shouldThrowOverflowExceptionForToLargeValueLength()
    {
        // expect
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("Unable to allocate map data buffer");

        // when
        new BucketArray(8, SIZE_OF_LONG, 1 << 55);
    }

    @Test
    public void shouldCloseBucketArray()
    {
        // given bucketArray
        final BucketArray bucketArray = new BucketArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
        bucketArray.allocateNewBucket(1, 1);

        // when
        bucketArray.close();

        // then next access is still possible
        // TODO add access checks
        bucketArray.getBucketCount();
    }

    @Test
    public void shouldCreateBucket()
    {
        // given
        final int firstBucketAddress = bucketArray.getFirstBucketAddress();
        assertThat(firstBucketAddress).isEqualTo(BUCKET_BUFFER_HEADER_LENGTH);

        // when
        final long newBucketAddress = bucketArray.allocateNewBucket(1, 1);

        // then
        assertThat(newBucketAddress).isEqualTo(firstBucketAddress);
        assertThat(bucketArray.getBucketCount()).isEqualTo(1);
        assertThat(bucketArray.getLength()).isEqualTo(BUCKET_BUFFER_HEADER_LENGTH + ALLOCATION_FACTOR * bucketArray.getMaxBucketLength());
        assertThat(bucketArray.getBucketDepth(firstBucketAddress)).isEqualTo(1);
        assertThat(bucketArray.getBucketId(firstBucketAddress)).isEqualTo(1);
        assertThat(bucketArray.getBucketLength(firstBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET);
        assertThat(bucketArray.getOccupiedBlocks()).isEqualTo(0);
    }

    @Test
    public void shouldIncreaseMemoryOnCreatingBuckets()
    {
        // given
        for (int i = 0; i < ALLOCATION_FACTOR; i++)
        {
            bucketArray.allocateNewBucket(i, i);
        }

        // when
        final long newBucketAddress = bucketArray.allocateNewBucket(0xFF, 0xFF);

        // then
        assertThat(newBucketAddress).isEqualTo(BUCKET_BUFFER_HEADER_LENGTH + ALLOCATION_FACTOR * bucketArray.getMaxBucketLength());

        assertThat(bucketArray.getBucketCount()).isEqualTo(ALLOCATION_FACTOR + 1);
        assertThat(bucketArray.getLength()).isEqualTo(BUCKET_BUFFER_HEADER_LENGTH + ((2 * ALLOCATION_FACTOR)  * bucketArray.getMaxBucketLength()));

        final int firstBucketAddress = bucketArray.getFirstBucketAddress();
        for (int i = 0; i < ALLOCATION_FACTOR; i++)
        {
            final long bucketAddress = firstBucketAddress + i * bucketArray.getMaxBucketLength();
            assertThat(bucketArray.getBucketDepth(bucketAddress)).isEqualTo(i);
            assertThat(bucketArray.getBucketId(bucketAddress)).isEqualTo(i);
            assertThat(bucketArray.getBucketLength(bucketAddress)).isEqualTo(BUCKET_DATA_OFFSET);
        }
        assertThat(bucketArray.getBucketDepth(newBucketAddress)).isEqualTo(0xFF);
        assertThat(bucketArray.getBucketId(newBucketAddress)).isEqualTo(0xFF);
        assertThat(bucketArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET);
        assertThat(bucketArray.getOccupiedBlocks()).isEqualTo(0);
    }

    @Test
    public void shouldClearBucketArray()
    {
        // given bucketArray
        final long newBucketAddress = bucketArray.allocateNewBucket(1, 1);

        // when
        bucketArray.clear();

        // then
        assertThat(bucketArray.getBucketCount()).isEqualTo(0);
        assertThat(bucketArray.getLength()).isEqualTo(BUCKET_BUFFER_HEADER_LENGTH + ALLOCATION_FACTOR * bucketArray.getMaxBucketLength());
        assertThat(bucketArray.getBucketDepth(newBucketAddress)).isEqualTo(0);
        assertThat(bucketArray.getBucketId(newBucketAddress)).isEqualTo(0);
        assertThat(bucketArray.getBucketLength(newBucketAddress)).isEqualTo(0);
        assertThat(bucketArray.getOccupiedBlocks()).isEqualTo(0);
    }

    @Test
    public void shouldCreateBlock()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketArray.allocateNewBucket(1, 1);

        // when
        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        final boolean wasAdded = bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketArray.getFirstBlockOffset(newBucketAddress);

        // then
        assertThat(wasAdded).isTrue();
        assertThat(newBlockOffset).isEqualTo(ZbMapDescriptor.BUCKET_DATA_OFFSET);
        assertThat(bucketArray.getBucketCount()).isEqualTo(1);
        assertThat(bucketArray.getLength()).isEqualTo(BUCKET_BUFFER_HEADER_LENGTH + ALLOCATION_FACTOR * bucketArray.getMaxBucketLength());
        assertThat(bucketArray.getBucketDepth(newBucketAddress)).isEqualTo(1);
        assertThat(bucketArray.getBucketId(newBucketAddress)).isEqualTo(1);
        assertThat(bucketArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET + getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketArray.getBucketFillCount(newBucketAddress)).isEqualTo(1);
        assertThat(bucketArray.getOccupiedBlocks()).isEqualTo(1);

        assertThat(bucketArray.getBlockLength(newBucketAddress, newBlockOffset)).isEqualTo(getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketArray.getBlockValueLength(newBucketAddress, newBlockOffset)).isEqualTo(SIZE_OF_LONG);

        final boolean keyEquals = bucketArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();

        bucketArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);

        bucketArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);
    }

    @Test
    public void shouldRemoveBlock()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketArray.allocateNewBucket(1, 1);
        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketArray.getFirstBlockOffset(newBucketAddress);

        // when
        bucketArray.removeBlock(newBucketAddress, newBlockOffset);

        // then
        assertThat(bucketArray.getOccupiedBlocks()).isEqualTo(0);
        assertThat(bucketArray.getBucketFillCount(newBucketAddress)).isEqualTo(0);
        assertThat(bucketArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET);
    }

    @Test
    public void shouldAddMoreBlocksThanMinimalFitSize()
    {
        // given bucket array with 2 blocks
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final ByteArrayValueHandler valueHandler = new ByteArrayValueHandler();
        final long newBucketAddress = bucketArray.allocateNewBucket(1, 1);
        keyHandler.theKey = 10;
        valueHandler.theValue = "1".getBytes();
        boolean wasAdded = bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        assertThat(wasAdded).isTrue();
        wasAdded = bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        assertThat(wasAdded).isTrue();

        // when
        wasAdded = bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        // then
        assertThat(wasAdded).isTrue();
        assertThat(bucketArray.getBucketCount()).isEqualTo(1);
        assertThat(bucketArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET + 3 * getBlockLength(MAX_KEY_LEN, 1));
        assertThat(bucketArray.getBucketFillCount(newBucketAddress)).isEqualTo(3);
        assertThat(bucketArray.getOccupiedBlocks()).isEqualTo(3);
    }



    @Test
    public void shouldUpdateBlockValue()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketArray.allocateNewBucket(1, 1);
        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketArray.getFirstBlockOffset(newBucketAddress);

        // when
        valueHandler.theValue = 0xAA;
        bucketArray.updateValue(valueHandler, newBucketAddress, newBlockOffset);

        // then
        assertThat(bucketArray.getBlockLength(newBucketAddress, newBlockOffset)).isEqualTo(getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketArray.getBlockValueLength(newBucketAddress, newBlockOffset)).isEqualTo(SIZE_OF_LONG);

        final boolean keyEquals = bucketArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();

        bucketArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);

        bucketArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xAA);
    }

    @Test
    public void shouldUpdateBlockValueWithSmallerValue()
    {
        // given bucket array with 1 bucket and two blocks
        bucketArray = new BucketArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final ByteArrayValueHandler valueHandler = new ByteArrayValueHandler();
        final long newBucketAddress = bucketArray.allocateNewBucket(1, 1);
        keyHandler.theKey = 10;
        valueHandler.theValue = "hallo".getBytes();
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        keyHandler.theKey = 11;
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketArray.getFirstBlockOffset(newBucketAddress);

        // when
        valueHandler.theValue = "new".getBytes();
        final boolean wasUpdated = bucketArray.updateValue(valueHandler, newBucketAddress, newBlockOffset);

        // then
        assertThat(wasUpdated).isTrue();

        // updated block
        assertThat(bucketArray.getBlockLength(newBucketAddress, newBlockOffset)).isEqualTo(getBlockLength(MAX_KEY_LEN, 3));
        assertThat(bucketArray.getBlockValueLength(newBucketAddress, newBlockOffset)).isEqualTo(3);

        keyHandler.theKey = 10;
        boolean keyEquals = bucketArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();
        bucketArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);
        bucketArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo("new".getBytes());

        // old block
        final int oldBlockOffset = newBlockOffset + bucketArray.getBlockLength(newBucketAddress, newBlockOffset);
        assertThat(bucketArray.getBlockLength(newBucketAddress, oldBlockOffset)).isEqualTo(getBlockLength(MAX_KEY_LEN, 5));

        keyHandler.theKey = 11;
        keyEquals = bucketArray.keyEquals(keyHandler, newBucketAddress, oldBlockOffset);
        assertThat(keyEquals).isTrue();
        bucketArray.readKey(keyHandler, newBucketAddress, oldBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(11);

        valueHandler.theValue = new byte[5];
        bucketArray.readValue(valueHandler, newBucketAddress, oldBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo("hallo".getBytes());
    }

    @Test
    public void shouldUpdateBlockValueWithLargerValue()
    {
        // given bucket array with 1 bucket and two blocks
        bucketArray = new BucketArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final ByteArrayValueHandler valueHandler = new ByteArrayValueHandler();
        final long newBucketAddress = bucketArray.allocateNewBucket(1, 1);
        keyHandler.theKey = 10;
        valueHandler.theValue = "old".getBytes();
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        keyHandler.theKey = 11;
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketArray.getFirstBlockOffset(newBucketAddress);

        // when
        valueHandler.theValue = "hallo".getBytes();
        final boolean wasUpdated = bucketArray.updateValue(valueHandler, newBucketAddress, newBlockOffset);

        // then
        assertThat(wasUpdated).isTrue();

        // updated block
        assertThat(bucketArray.getBlockLength(newBucketAddress, newBlockOffset)).isEqualTo(getBlockLength(MAX_KEY_LEN, 5));
        assertThat(bucketArray.getBlockValueLength(newBucketAddress, newBlockOffset)).isEqualTo(5);

        keyHandler.theKey = 10;
        boolean keyEquals = bucketArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();
        bucketArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);
        bucketArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo("hallo".getBytes());

        // old block
        final int oldBlockOffset = newBlockOffset + bucketArray.getBlockLength(newBucketAddress, newBlockOffset);
        assertThat(bucketArray.getBlockLength(newBucketAddress, oldBlockOffset)).isEqualTo(getBlockLength(MAX_KEY_LEN, 3));

        keyHandler.theKey = 11;
        keyEquals = bucketArray.keyEquals(keyHandler, newBucketAddress, oldBlockOffset);
        assertThat(keyEquals).isTrue();
        bucketArray.readKey(keyHandler, newBucketAddress, oldBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(11);

        valueHandler.theValue = new byte[3];
        bucketArray.readValue(valueHandler, newBucketAddress, oldBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo("old".getBytes());
    }

    @Test
    public void shouldThrowOnUpdateBlockValueWithTooLargerValue()
    {
        // given bucket array with 1 bucket and two blocks
        bucketArray = new BucketArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final ByteArrayValueHandler valueHandler = new ByteArrayValueHandler();
        final long newBucketAddress = bucketArray.allocateNewBucket(1, 1);
        keyHandler.theKey = 10;
        valueHandler.theValue = "old".getBytes();
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        keyHandler.theKey = 11;
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketArray.getFirstBlockOffset(newBucketAddress);

        // expect
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Value can't exceed the max value length of 8");

        // when
        valueHandler.theValue = "toLargeValue1234".getBytes();
        bucketArray.updateValue(valueHandler, newBucketAddress, newBlockOffset);
    }

    @Test
    public void shouldUpdateBlockAndIncreaseMaxBucketSize()
    {
        // given bucket array with 3 blocks
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final ByteArrayValueHandler valueHandler = new ByteArrayValueHandler();
        final long newBucketAddress = bucketArray.allocateNewBucket(1, 1);
        keyHandler.theKey = 10;
        valueHandler.theValue = "1".getBytes();
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketArray.getFirstBlockOffset(newBucketAddress);

        // when
        valueHandler.theValue = "12".getBytes();
        boolean wasUpdated = bucketArray.updateValue(valueHandler, newBucketAddress, newBlockOffset);

        // then
        assertThat(wasUpdated).isTrue();

        // when update increase bucket size
        valueHandler.theValue = "123".getBytes();
        wasUpdated = bucketArray.updateValue(valueHandler, newBucketAddress, newBlockOffset);

        // then
        assertThat(wasUpdated).isFalse();
    }

    @Test
    public void shouldRelocateBlock()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long firstBucketAddress = bucketArray.allocateNewBucket(1, 1);
        final long newBucketAddress = bucketArray.allocateNewBucket(1, 1);
        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketArray.addBlock(firstBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketArray.getFirstBlockOffset(firstBucketAddress);

        // when
        bucketArray.relocateBlock(firstBucketAddress, newBlockOffset, newBucketAddress);

        // then
        assertThat(bucketArray.getBucketLength(firstBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET);
        assertThat(bucketArray.getBucketFillCount(firstBucketAddress)).isEqualTo(0);

        assertThat(bucketArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET + getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketArray.getBucketFillCount(newBucketAddress)).isEqualTo(1);
        assertThat(bucketArray.getOccupiedBlocks()).isEqualTo(1);

        assertThat(bucketArray.getBlockLength(newBucketAddress, newBlockOffset)).isEqualTo(getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketArray.getBlockValueLength(newBucketAddress, newBlockOffset)).isEqualTo(SIZE_OF_LONG);

        final boolean keyEquals = bucketArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();

        bucketArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);

        bucketArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);
    }

    @Test
    public void shouldRelocateBlockToBucketWhichIsHalfFull()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long firstBucketAddress = bucketArray.allocateNewBucket(1, 1);
        final long newBucketAddress = bucketArray.allocateNewBucket(1, 1);
        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketArray.addBlock(firstBucketAddress, keyHandler, valueHandler);
        keyHandler.theKey = 11;
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        int newBlockOffset = bucketArray.getFirstBlockOffset(firstBucketAddress);

        // when
        bucketArray.relocateBlock(firstBucketAddress, newBlockOffset, newBucketAddress);

        // then
        assertThat(bucketArray.getBucketLength(firstBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET);
        assertThat(bucketArray.getBucketFillCount(firstBucketAddress)).isEqualTo(0);

        assertThat(bucketArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET + 2 * getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketArray.getBucketFillCount(newBucketAddress)).isEqualTo(2);
        assertThat(bucketArray.getOccupiedBlocks()).isEqualTo(2);

        final int blockLength = bucketArray.getBlockLength(newBucketAddress, newBlockOffset);
        assertThat(blockLength).isEqualTo(getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketArray.getBlockValueLength(newBucketAddress, newBlockOffset)).isEqualTo(SIZE_OF_LONG);

        boolean keyEquals = bucketArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();

        bucketArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(11);

        bucketArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);

        newBlockOffset += blockLength;
        assertThat(blockLength).isEqualTo(getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketArray.getBlockValueLength(newBucketAddress, newBlockOffset)).isEqualTo(SIZE_OF_LONG);

        keyHandler.theKey = 10;
        keyEquals = bucketArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();

        bucketArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);

        bucketArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);
    }

    @Test
    public void shouldOverflowBucketOnRelocate()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long firstBucketAddress = bucketArray.allocateNewBucket(1, 1);
        final long newBucketAddress = bucketArray.allocateNewBucket(2, 2);
        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketArray.addBlock(firstBucketAddress, keyHandler, valueHandler);
        keyHandler.theKey = 11;
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        keyHandler.theKey = 12;
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketArray.getFirstBlockOffset(firstBucketAddress);

        // when
        bucketArray.relocateBlock(firstBucketAddress, newBlockOffset, newBucketAddress);

        // then new bucket overflows
        final long bucketOverflowPointer = bucketArray.getBucketOverflowPointer(newBucketAddress);
        assertThat(bucketOverflowPointer).isGreaterThan(0);
        assertThat(bucketArray.getBucketDepth(bucketOverflowPointer)).isEqualTo(0);
        assertThat(bucketArray.getBucketId(bucketOverflowPointer)).isEqualTo(BucketArray.OVERFLOW_BUCKET_ID);

        assertThat(bucketArray.getBucketFillCount(newBucketAddress)).isEqualTo(2);
        assertThat(bucketArray.getBucketFillCount(bucketOverflowPointer)).isEqualTo(1);
        assertThat(bucketArray.getBucketFillCount(firstBucketAddress)).isEqualTo(0);

        assertThat(bucketArray.getOccupiedBlocks()).isEqualTo(3);

        // and block is equal to
        keyHandler.theKey = 10;
        final boolean keyEquals = bucketArray.keyEquals(keyHandler, bucketOverflowPointer, newBlockOffset);
        assertThat(keyEquals).isTrue();

        bucketArray.readKey(keyHandler, bucketOverflowPointer, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);

        bucketArray.readValue(valueHandler, bucketOverflowPointer, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);
    }

    @Test
    public void shouldOverflowBucket()
    {
        // given
        final long newBucketAddress = bucketArray.allocateNewBucket(1, 1);

        // when
        final long overflowBucketAddress = bucketArray.overflow(newBucketAddress);

        // then
        assertThat(bucketArray.getBucketOverflowPointer(newBucketAddress)).isEqualTo(overflowBucketAddress);

        assertThat(bucketArray.getBucketOverflowPointer(overflowBucketAddress)).isEqualTo(0);
        assertThat(bucketArray.getBucketCount()).isEqualTo(2);
        assertThat(bucketArray.getOccupiedBlocks()).isEqualTo(0);
        assertThat(bucketArray.getBucketDepth(overflowBucketAddress)).isEqualTo(0);
        assertThat(bucketArray.getBucketId(overflowBucketAddress)).isEqualTo(BucketArray.OVERFLOW_BUCKET_ID);
    }

    @Test
    public void shouldUseOverflowBucketIfAddBlockOnFullBucket()
    {
        // given
        final long newBucketAddress = bucketArray.allocateNewBucket(1, 1);
        final long overflowBucketAddress = bucketArray.overflow(newBucketAddress);
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        keyHandler.theKey = 11;
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        // when
        keyHandler.theKey = 12;
        valueHandler.theValue = 0xFF;
        final boolean wasAdded = bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketArray.getFirstBlockOffset(overflowBucketAddress);

        // then
        assertThat(wasAdded).isTrue();
        assertThat(newBlockOffset).isEqualTo(ZbMapDescriptor.BUCKET_DATA_OFFSET);

        assertThat(bucketArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET + 2 * getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketArray.getBucketFillCount(newBucketAddress)).isEqualTo(2); // full

        assertThat(bucketArray.getBucketLength(overflowBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET + getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketArray.getBucketFillCount(overflowBucketAddress)).isEqualTo(1);
        assertThat(bucketArray.getOccupiedBlocks()).isEqualTo(3);

        final boolean keyEquals = bucketArray.keyEquals(keyHandler, overflowBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();

        bucketArray.readKey(keyHandler, overflowBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(12);

        bucketArray.readValue(valueHandler, overflowBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);
    }


    @Test
    public void shouldAddBlockOnOverflowBucket()
    {
        // given
        final long newBucketAddress = bucketArray.allocateNewBucket(1, 1);
        final long overflowBucketAddress = bucketArray.overflow(newBucketAddress);
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();

        // when
        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        final boolean wasAdded = bucketArray.addBlock(overflowBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketArray.getFirstBlockOffset(overflowBucketAddress);

        // then
        assertThat(wasAdded).isTrue();
        assertThat(newBlockOffset).isEqualTo(ZbMapDescriptor.BUCKET_DATA_OFFSET);

        assertThat(bucketArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET);
        assertThat(bucketArray.getBucketFillCount(newBucketAddress)).isEqualTo(0);

        assertThat(bucketArray.getBucketLength(overflowBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET + getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketArray.getBucketFillCount(overflowBucketAddress)).isEqualTo(1);
        assertThat(bucketArray.getOccupiedBlocks()).isEqualTo(1);

        final boolean keyEquals = bucketArray.keyEquals(keyHandler, overflowBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();

        bucketArray.readKey(keyHandler, overflowBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);

        bucketArray.readValue(valueHandler, overflowBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);
    }

    @Test
    public void shouldOverflowOverflowBucket()
    {
        // given
        final long newBucketAddress = bucketArray.allocateNewBucket(1, 1);
        final long overflowBucketAddress = bucketArray.overflow(newBucketAddress);

        // when
        final long newOverflowBucketAddress = bucketArray.overflow(newBucketAddress);

        // then
        assertThat(bucketArray.getBucketCount()).isEqualTo(3);
        assertThat(bucketArray.getOccupiedBlocks()).isEqualTo(0);
        assertThat(bucketArray.getBucketOverflowPointer(newBucketAddress)).isEqualTo(overflowBucketAddress);

        assertThat(bucketArray.getBucketDepth(overflowBucketAddress)).isEqualTo(0);
        assertThat(bucketArray.getBucketId(overflowBucketAddress)).isEqualTo(BucketArray.OVERFLOW_BUCKET_ID);
        assertThat(bucketArray.getBucketOverflowPointer(overflowBucketAddress)).isEqualTo(newOverflowBucketAddress);

        assertThat(bucketArray.getBucketDepth(newOverflowBucketAddress)).isEqualTo(0);
        assertThat(bucketArray.getBucketId(newOverflowBucketAddress)).isEqualTo(BucketArray.OVERFLOW_BUCKET_ID);
        assertThat(bucketArray.getBucketOverflowPointer(newOverflowBucketAddress)).isEqualTo(0);
    }

    @Test
    public void shouldWriteBucketArrayToStream() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketArray.allocateNewBucket(3, 3);

        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketArray.getFirstBlockOffset(newBucketAddress);

        // when
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[16];
        bucketArray.writeToStream(outputStream, writeBuffer);

        // then
        final byte[] writtenBytes = outputStream.toByteArray();

        assertThat(writtenBytes.length).isEqualTo((int) bucketArray.getLength());
        assertThat(writtenBytes[BUCKET_COUNT_OFFSET]).isEqualTo((byte) 1);

        // we can check the values simply since we use little endianness
        assertThat(writtenBytes[(int) newBucketAddress]).isEqualTo((byte) 1);
        assertThat(writtenBytes[(int) newBucketAddress + ZbMapDescriptor.BUCKET_ID_OFFSET]).isEqualTo((byte) 3);
        assertThat(writtenBytes[(int) newBucketAddress + ZbMapDescriptor.BUCKET_DEPTH_OFFSET]).isEqualTo((byte) 3);

        assertThat(writtenBytes[(int) newBucketAddress + newBlockOffset]).isEqualTo((byte) 8);
        assertThat(writtenBytes[(int) newBucketAddress + newBlockOffset + BLOCK_KEY_OFFSET]).isEqualTo((byte) 10);

        assertThat(writtenBytes[(int) newBucketAddress + newBlockOffset + BLOCK_KEY_OFFSET + SIZE_OF_LONG]).isEqualTo((byte) 0xFF);
    }

    @Test
    public void shouldReadBucketArrayFromStream() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketArray.allocateNewBucket(3, 3);

        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketArray.getFirstBlockOffset(newBucketAddress);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[16];
        bucketArray.writeToStream(outputStream, writeBuffer);

        // when
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        final BucketArray newBucketArray = new BucketArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
        newBucketArray.readFromStream(inputStream, writeBuffer);

        // then
        assertThat(newBucketArray.getLength()).isEqualTo((int) bucketArray.getLength());
        assertThat(newBucketArray.getBucketCount()).isEqualTo(1);

        assertThat(newBucketArray.getBucketFillCount(newBucketAddress)).isEqualTo(1);
        assertThat(newBucketArray.getBucketId(newBucketAddress)).isEqualTo(3);
        assertThat(newBucketArray.getBucketDepth(newBucketAddress)).isEqualTo(3);

        assertThat(newBucketArray.getBlockValueLength(newBucketAddress, newBlockOffset)).isEqualTo(8);

        newBucketArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);

        newBucketArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);
        newBucketArray.close();
    }

    @Test
    public void shouldOverwriteWithReadBucketArrayFromStream() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketArray.allocateNewBucket(3, 3);

        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketArray.getFirstBlockOffset(newBucketAddress);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[16];
        bucketArray.writeToStream(outputStream, writeBuffer);

        // when
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        final BucketArray newBucketArray = new BucketArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
        bucketArray.allocateNewBucket(4, 4);
        keyHandler.theKey = 14;
        valueHandler.theValue = 0xAA;
        bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        bucketArray.getFirstBlockOffset(newBucketAddress);
        newBucketArray.readFromStream(inputStream, writeBuffer);

        // then
        assertThat(newBucketArray.getLength()).isEqualTo((int) bucketArray.getLength());
        assertThat(newBucketArray.getBucketCount()).isEqualTo(1);

        assertThat(newBucketArray.getBucketFillCount(newBucketAddress)).isEqualTo(1);
        assertThat(newBucketArray.getBucketId(newBucketAddress)).isEqualTo(3);
        assertThat(newBucketArray.getBucketDepth(newBucketAddress)).isEqualTo(3);

        assertThat(newBucketArray.getBlockValueLength(newBucketAddress, newBlockOffset)).isEqualTo(8);

        newBucketArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);

        newBucketArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);
        newBucketArray.close();
    }

    @Test
    public void shouldReadLargeBucketArrayFromStream() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        for (int i = 0; i < ALLOCATION_FACTOR + 1; i++)
        {
            final long newBucketAddress = bucketArray.allocateNewBucket(3, 3);
            keyHandler.theKey = 10;
            valueHandler.theValue = 0xFF;
            bucketArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        }

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[16];
        bucketArray.writeToStream(outputStream, writeBuffer);

        // when
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        final BucketArray newBucketArray = new BucketArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
        newBucketArray.readFromStream(inputStream, writeBuffer);

        // then
        assertThat(newBucketArray.getLength()).isEqualTo((int) bucketArray.getLength());
        assertThat(newBucketArray.getBucketCount()).isEqualTo(33);
        newBucketArray.close();
    }
}

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

import static io.zeebe.map.BucketBufferArray.*;
import static io.zeebe.map.BucketBufferArrayDescriptor.*;
import static io.zeebe.test.util.BufferAssert.assertThatBuffer;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.zeebe.map.types.ByteArrayValueHandler;
import io.zeebe.map.types.LongKeyHandler;
import io.zeebe.map.types.LongValueHandler;
import io.zeebe.util.buffer.BufferUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 *
 */
public class BucketBufferArrayTest
{
    private static final int MAX_KEY_LEN = SIZE_OF_LONG;
    private static final int MAX_VALUE_LEN = SIZE_OF_LONG;
    private static final int MIN_BLOCK_COUNT = 2;

    protected BucketBufferArray bucketBufferArray;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void init()
    {
        bucketBufferArray = new BucketBufferArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
    }

    @After
    public void close()
    {
        bucketBufferArray.close();
    }


    @Test
    public void shouldCreateBucketArray()
    {
        // given bucketBufferArray
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getCapacity()).isEqualTo(BUCKET_BUFFER_HEADER_LENGTH + ALLOCATION_FACTOR * bucketBufferArray.getMaxBucketLength());
    }

    @Test
    public void shouldThrowOverflowExceptionForToLargeMinBlockCount()
    {
        // expect
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Maximum bucket buffer length exceeds integer maximum value.");

        // when
        new BucketBufferArray(1 << 54, SIZE_OF_LONG, SIZE_OF_LONG);
    }

    @Test
    public void shouldThrowOverflowExceptionForToLargeKeyLength()
    {
        // expect
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Maximum bucket buffer length exceeds integer maximum value.");

        // when
        new BucketBufferArray(8, 1 << 55, SIZE_OF_LONG);
    }

    @Test
    public void shouldThrowOverflowExceptionForToLargeValueLength()
    {
        // expect
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Maximum bucket buffer length exceeds integer maximum value.");

        // when
        new BucketBufferArray(8, SIZE_OF_LONG, 1 << 55);
    }

    @Test
    public void shouldCloseBucketArray()
    {
        // given bucketBufferArray
        final BucketBufferArray bucketBufferArray = new BucketBufferArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
        bucketBufferArray.allocateNewBucket(1, 1);

        // when
        bucketBufferArray.close();

        // then next access is still possible
        // TODO add access checks
        bucketBufferArray.getBucketCount();
    }

    @Test
    public void shouldCreateBucket()
    {
        // given
        final int firstBucketAddress = bucketBufferArray.getFirstBucketOffset();
        assertThat(firstBucketAddress).isEqualTo(BUCKET_BUFFER_HEADER_LENGTH);

        // when
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        // then
        assertThat(newBucketAddress).isEqualTo(firstBucketAddress);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(1);
        assertThat(bucketBufferArray.getCapacity()).isEqualTo(BUCKET_BUFFER_HEADER_LENGTH + ALLOCATION_FACTOR * bucketBufferArray.getMaxBucketLength());
        assertThat(bucketBufferArray.getBucketDepth(firstBucketAddress)).isEqualTo(1);
        assertThat(bucketBufferArray.getBucketId(firstBucketAddress)).isEqualTo(1);
        assertThat(bucketBufferArray.getBucketLength(firstBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
    }

    @Test
    public void shouldAllocateNewBucketBufferOnCreatingBuckets()
    {
        // given
        allocateBuckets(ALLOCATION_FACTOR);

        // when
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(0xFF, 0xFF);

        // then
        assertThat(newBucketAddress).isEqualTo(getBucketAddress(1, BUCKET_BUFFER_HEADER_LENGTH));

        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(ALLOCATION_FACTOR + 1);
        assertThat(bucketBufferArray.getCapacity()).isEqualTo(bucketBufferArray.getMaxBucketBufferLength() * 2);

        final int firstBucketAddress = bucketBufferArray.getFirstBucketOffset();
        for (int i = 0; i < ALLOCATION_FACTOR; i++)
        {
            final int bucketOffset = firstBucketAddress + i * bucketBufferArray.getMaxBucketLength();
            final long bucketAddress = BucketBufferArray.getBucketAddress(0, bucketOffset);

            assertThat(bucketBufferArray.getBucketDepth(bucketAddress)).isEqualTo(i);
            assertThat(bucketBufferArray.getBucketId(bucketAddress)).isEqualTo(i);
            assertThat(bucketBufferArray.getBucketLength(bucketAddress)).isEqualTo(BUCKET_DATA_OFFSET);
        }
        assertThat(bucketBufferArray.getBucketDepth(newBucketAddress)).isEqualTo(0xFF);
        assertThat(bucketBufferArray.getBucketId(newBucketAddress)).isEqualTo(0xFF);
        assertThat(bucketBufferArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
    }

    @Test
    public void shouldAllocateBucketOnBufferWhichHasFreeBuckets()
    {
        // given
        fillBucketBufferArray(3 * ALLOCATION_FACTOR);

        // remove some buckets on second buffer
        for (int i = ALLOCATION_FACTOR - 1; i >= ALLOCATION_FACTOR - 11; i--)
        {
            final int bucketOffset = i * bucketBufferArray.getMaxBucketLength() + BucketBufferArrayDescriptor.BUCKET_BUFFER_HEADER_LENGTH;
            final long bucketAddress = getBucketAddress(1, bucketOffset);
            bucketBufferArray.removeBlock(bucketAddress, bucketBufferArray.getFirstBlockOffset());
            bucketBufferArray.removeBlock(bucketAddress, bucketBufferArray.getFirstBlockOffset() + bucketBufferArray.getBlockLength());
            bucketBufferArray.removeBucket(bucketAddress);
        }

        // when
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(0xFF, 0xFF);

        // then
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(3);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(86);
        assertThat(bucketBufferArray.getBucketCount(0)).isEqualTo(32);
        assertThat(bucketBufferArray.getBucketCount(1)).isEqualTo(22);
        assertThat(bucketBufferArray.getBucketCount(2)).isEqualTo(32);

        final int bucketOffset = (ALLOCATION_FACTOR - 11) * bucketBufferArray.getMaxBucketLength() + BucketBufferArrayDescriptor.BUCKET_BUFFER_HEADER_LENGTH;
        assertThat(newBucketAddress).isEqualTo(getBucketAddress(1,  bucketOffset));
    }

    @Test
    public void shouldAllocateBucketOnLastBufferAfterAllFreeBucketsAreUsed()
    {
        // given
        fillBucketBufferArray(3 * ALLOCATION_FACTOR);

        // remove some buckets on second buffer
        for (int i = ALLOCATION_FACTOR - 1; i >= ALLOCATION_FACTOR - 11; i--)
        {
            final int bucketOffset = i * bucketBufferArray.getMaxBucketLength() + BucketBufferArrayDescriptor.BUCKET_BUFFER_HEADER_LENGTH;
            final long bucketAddress = getBucketAddress(1, bucketOffset);
            bucketBufferArray.removeBlock(bucketAddress, bucketBufferArray.getFirstBlockOffset());
            bucketBufferArray.removeBlock(bucketAddress, bucketBufferArray.getFirstBlockOffset() + bucketBufferArray.getBlockLength());
            bucketBufferArray.removeBucket(bucketAddress);
        }

        // allocate bucket in second buffer
        allocateBuckets(11);

        // when
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(0xFF, 0xFF);

        // then
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(4);

        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(97);
        assertThat(bucketBufferArray.getBucketCount(0)).isEqualTo(32);
        assertThat(bucketBufferArray.getBucketCount(1)).isEqualTo(32);
        assertThat(bucketBufferArray.getBucketCount(2)).isEqualTo(32);
        assertThat(bucketBufferArray.getBucketCount(3)).isEqualTo(1);

        final int bucketOffset = BucketBufferArrayDescriptor.BUCKET_BUFFER_HEADER_LENGTH;
        assertThat(newBucketAddress).isEqualTo(getBucketAddress(3,  bucketOffset));
    }

    @Test
    public void shouldIncreaseAddressArrayOnCreatingBuckets()
    {
        // given
        // default address array is 32 - so in the begin we can create 32 bucket buffers after that the
        // array will be doubled
        allocateBuckets(32 * 32);

        // when
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(0xFF, 0xFF);

        // then address array is increased so we can create new bucket buffer
        assertThat(newBucketAddress).isEqualTo(getBucketAddress(32, bucketBufferArray.getFirstBucketOffset()));
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(33);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(32 * 32 + 1);
    }

    @Test
    public void shouldClearBucketArray()
    {
        // given bucketBufferArray
        bucketBufferArray.allocateNewBucket(1, 1);

        // when
        bucketBufferArray.clear();

        // then only count and overflow pointers are set to zero
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getCapacity()).isEqualTo(BUCKET_BUFFER_HEADER_LENGTH + ALLOCATION_FACTOR * bucketBufferArray.getMaxBucketLength());
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
    }

    @Test
    public void shouldThrowExceptionOnAddBlockIfNoBucketWasAllocated()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long firstBucketAddress = 4;

        // expect
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("No bucket in buffer 0, need to allocate new bucket!");

        // when
        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketBufferArray.addBlock(firstBucketAddress, keyHandler, valueHandler);
    }


    @Test
    public void shouldCreateBlock()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        // when
        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        final boolean wasAdded = bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();

        // then
        assertThat(wasAdded).isTrue();
        assertThat(newBlockOffset).isEqualTo(BUCKET_DATA_OFFSET);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(1);
        assertThat(bucketBufferArray.getCapacity()).isEqualTo(BUCKET_BUFFER_HEADER_LENGTH + ALLOCATION_FACTOR * bucketBufferArray.getMaxBucketLength());
        assertThat(bucketBufferArray.getBucketDepth(newBucketAddress)).isEqualTo(1);
        assertThat(bucketBufferArray.getBucketId(newBucketAddress)).isEqualTo(1);
        assertThat(bucketBufferArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET + getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketBufferArray.getBucketFillCount(newBucketAddress)).isEqualTo(1);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(1);

        assertThat(bucketBufferArray.getBlockLength()).isEqualTo(getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));

        final boolean keyEquals = bucketBufferArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();

        bucketBufferArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);

        bucketBufferArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);
    }

    @Test
    public void shouldRemoveBlock()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        // when
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();
        bucketBufferArray.removeBlock(newBucketAddress, newBlockOffset);

        // then
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketFillCount(newBucketAddress)).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET);
    }

    @Test
    public void shouldThrowExceptionOnAddBlockAfterRemoveBucket()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        bucketBufferArray.removeBucket(newBucketAddress);

        // expect
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("No bucket in buffer 0, need to allocate new bucket!");

        // when
        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
    }

    @Test
    public void shouldAddMoreBlocksThanMinimalFitSize()
    {
        // given bucket array with 2 blocks
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final ByteArrayValueHandler valueHandler = new ByteArrayValueHandler();
        valueHandler.setValueLength(MAX_VALUE_LEN);

        final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        keyHandler.theKey = 10;
        valueHandler.setValue("1".getBytes());
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        // when
        final boolean wasAdded = bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        // then
        assertThat(wasAdded).isFalse();
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(1);
        assertThat(bucketBufferArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET + 2 * getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketBufferArray.getBucketFillCount(newBucketAddress)).isEqualTo(2);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(2);
    }

    @Test
    public void shouldUpdateBlockValue()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();

        final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        // when
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();
        valueHandler.theValue = 0xAA;
        bucketBufferArray.updateValue(valueHandler, newBucketAddress, newBlockOffset);

        // then
        assertThat(bucketBufferArray.getBlockLength()).isEqualTo(getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));

        final boolean keyEquals = bucketBufferArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();

        bucketBufferArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);

        bucketBufferArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xAA);
    }

    @Test
    public void shouldUpdateBlockValueWithSmallerValue()
    {
        // given bucket array with 1 bucket and two blocks
        bucketBufferArray = new BucketBufferArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final ByteArrayValueHandler valueHandler = new ByteArrayValueHandler();
        valueHandler.setValueLength(MAX_VALUE_LEN);

        final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        keyHandler.theKey = 10;
        byte[] value = "hallo".getBytes();
        valueHandler.setValue(value);
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        keyHandler.theKey = 11;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        // when
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();
        value = "new".getBytes();
        valueHandler.setValue(value);
        bucketBufferArray.updateValue(valueHandler, newBucketAddress, newBlockOffset);

        // then updated block
        assertThat(bucketBufferArray.getBlockLength()).isEqualTo(getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));

        keyHandler.theKey = 10;
        boolean keyEquals = bucketBufferArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();
        bucketBufferArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);
        bucketBufferArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(value).isEqualTo("new".getBytes());

        // old block
        final int oldBlockOffset = newBlockOffset + bucketBufferArray.getBlockLength();
        assertThat(bucketBufferArray.getBlockLength()).isEqualTo(getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));

        keyHandler.theKey = 11;
        keyEquals = bucketBufferArray.keyEquals(keyHandler, newBucketAddress, oldBlockOffset);
        assertThat(keyEquals).isTrue();
        bucketBufferArray.readKey(keyHandler, newBucketAddress, oldBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(11);

        bucketBufferArray.readValue(valueHandler, newBucketAddress, oldBlockOffset);
        assertThatBuffer(valueHandler.getValue()).hasBytes(BufferUtil.wrapString("hallo"));
    }

    @Test
    public void shouldUpdateBlockValueWithLargerValue()
    {
        // given bucket array with 1 bucket and two blocks
        bucketBufferArray = new BucketBufferArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final ByteArrayValueHandler valueHandler = new ByteArrayValueHandler();
        valueHandler.setValueLength(MAX_VALUE_LEN);
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        keyHandler.theKey = 10;
        byte[] value = "old".getBytes();
        valueHandler.setValue(value);

        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        keyHandler.theKey = 11;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        // when
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();
        value = "hallo".getBytes();
        valueHandler.setValue(value);
        bucketBufferArray.updateValue(valueHandler, newBucketAddress, newBlockOffset);

        // then updated block
        assertThat(bucketBufferArray.getBlockLength()).isEqualTo(getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));

        keyHandler.theKey = 10;
        boolean keyEquals = bucketBufferArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();
        bucketBufferArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);
        bucketBufferArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(value).isEqualTo("hallo".getBytes());

        // old block
        final int oldBlockOffset = newBlockOffset + bucketBufferArray.getBlockLength();
        assertThat(bucketBufferArray.getBlockLength()).isEqualTo(getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));

        keyHandler.theKey = 11;
        keyEquals = bucketBufferArray.keyEquals(keyHandler, newBucketAddress, oldBlockOffset);
        assertThat(keyEquals).isTrue();
        bucketBufferArray.readKey(keyHandler, newBucketAddress, oldBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(11);

        bucketBufferArray.readValue(valueHandler, newBucketAddress, oldBlockOffset);
        assertThatBuffer(valueHandler.getValue()).hasBytes(BufferUtil.wrapString("old"));
    }

    @Test
    public void shouldThrowOnUpdateBlockValueWithTooLargerValue()
    {
        // given bucket array with 1 bucket and two blocks
        bucketBufferArray = new BucketBufferArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);

        final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        final LongKeyHandler keyHandler = new LongKeyHandler();
        keyHandler.theKey = 10;

        final ByteArrayValueHandler valueHandler = new ByteArrayValueHandler();
        valueHandler.setValueLength(MAX_VALUE_LEN + 1);

        final byte[] value = "old".getBytes();
        valueHandler.setValue(value);

        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();
        // expect
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Value can't exceed the max value length of 8");

        // when
        bucketBufferArray.updateValue(valueHandler, newBucketAddress, newBlockOffset);
    }

    @Test
    public void shouldRelocateBlock()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();

        final long firstBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(2, 1);

        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketBufferArray.addBlock(firstBucketAddress, keyHandler, valueHandler);

        // when
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();
        bucketBufferArray.relocateBlock(firstBucketAddress, newBlockOffset, newBucketAddress);

        // then
        assertThat(bucketBufferArray.getBucketLength(firstBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET);
        assertThat(bucketBufferArray.getBucketFillCount(firstBucketAddress)).isEqualTo(0);

        assertThat(bucketBufferArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET + getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketBufferArray.getBucketFillCount(newBucketAddress)).isEqualTo(1);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(1);

        assertThat(bucketBufferArray.getBlockLength()).isEqualTo(getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));

        final boolean keyEquals = bucketBufferArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();

        bucketBufferArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);

        bucketBufferArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);
    }

    @Test
    public void shouldRelocateBlockToBucketWhichIsHalfFull()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();

        final long firstBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketBufferArray.addBlock(firstBucketAddress, keyHandler, valueHandler);

        keyHandler.theKey = 11;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        // when
        int newBlockOffset = bucketBufferArray.getFirstBlockOffset();
        bucketBufferArray.relocateBlock(firstBucketAddress, newBlockOffset, newBucketAddress);

        // then
        assertThat(bucketBufferArray.getBucketLength(firstBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET);
        assertThat(bucketBufferArray.getBucketFillCount(firstBucketAddress)).isEqualTo(0);

        assertThat(bucketBufferArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET + 2 * getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketBufferArray.getBucketFillCount(newBucketAddress)).isEqualTo(2);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(2);

        final int blockLength = bucketBufferArray.getBlockLength();
        assertThat(blockLength).isEqualTo(getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));

        boolean keyEquals = bucketBufferArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();

        bucketBufferArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(11);

        bucketBufferArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);

        newBlockOffset += blockLength;
        assertThat(blockLength).isEqualTo(getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));

        keyHandler.theKey = 10;
        keyEquals = bucketBufferArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();

        bucketBufferArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);

        bucketBufferArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);
    }

    @Test
    public void shouldOverflowBucketOnRelocate()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();

        final long firstBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(2, 2);

        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketBufferArray.addBlock(firstBucketAddress, keyHandler, valueHandler);

        keyHandler.theKey = 11;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        keyHandler.theKey = 12;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        // when
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();
        bucketBufferArray.relocateBlock(firstBucketAddress, newBlockOffset, newBucketAddress);

        // then new bucket overflows
        final long bucketOverflowPointer = bucketBufferArray.getBucketOverflowPointer(newBucketAddress);
        assertThat(bucketOverflowPointer).isGreaterThan(0);
        assertThat(bucketBufferArray.getBucketDepth(bucketOverflowPointer)).isEqualTo(OVERFLOW_BUCKET);
        assertThat(bucketBufferArray.getBucketId(bucketOverflowPointer)).isEqualTo(2);

        assertThat(bucketBufferArray.getBucketFillCount(newBucketAddress)).isEqualTo(2);
        assertThat(bucketBufferArray.getBucketFillCount(bucketOverflowPointer)).isEqualTo(1);
        assertThat(bucketBufferArray.getBucketFillCount(firstBucketAddress)).isEqualTo(0);

        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(3);

        // and block is equal to
        keyHandler.theKey = 10;
        final boolean keyEquals = bucketBufferArray.keyEquals(keyHandler, bucketOverflowPointer, newBlockOffset);
        assertThat(keyEquals).isTrue();

        bucketBufferArray.readKey(keyHandler, bucketOverflowPointer, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);

        bucketBufferArray.readValue(valueHandler, bucketOverflowPointer, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);
    }

    @Test
    public void shouldRelocateBlocksFromBucket()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();

        final long firstBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(2, 1);

        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketBufferArray.addBlock(firstBucketAddress, keyHandler, valueHandler);

        keyHandler.theKey = 11;
        valueHandler.theValue = 0xAF;
        bucketBufferArray.addBlock(firstBucketAddress, keyHandler, valueHandler);

        // when
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();
        bucketBufferArray.relocateBlocksFromBucket(firstBucketAddress, newBucketAddress);

        // then
        assertThat(bucketBufferArray.getBucketLength(firstBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET);
        assertThat(bucketBufferArray.getBucketFillCount(firstBucketAddress)).isEqualTo(0);

        assertThat(bucketBufferArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET + 2 * getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketBufferArray.getBucketFillCount(newBucketAddress)).isEqualTo(2);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(2);

        final int blockLength = bucketBufferArray.getBlockLength();

        assertThat(bucketBufferArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset + blockLength)).isTrue();
        keyHandler.theKey = 10;
        assertThat(bucketBufferArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset)).isTrue();

        bucketBufferArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);

        bucketBufferArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);

        bucketBufferArray.readKey(keyHandler, newBucketAddress, newBlockOffset + blockLength);
        assertThat(keyHandler.theKey).isEqualTo(11);

        bucketBufferArray.readValue(valueHandler, newBucketAddress, newBlockOffset + blockLength);
        assertThat(valueHandler.theValue).isEqualTo(0xAF);
    }

    @Test
    public void shouldRelocateBlocksFromBucketToBucketWithBlocks()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();

        final long firstBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(2, 1);

        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketBufferArray.addBlock(firstBucketAddress, keyHandler, valueHandler);

        keyHandler.theKey = 11;
        valueHandler.theValue = 0xAF;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        // when
        bucketBufferArray.relocateBlocksFromBucket(firstBucketAddress, newBucketAddress);

        // then
        assertThat(bucketBufferArray.getBucketLength(firstBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET);
        assertThat(bucketBufferArray.getBucketFillCount(firstBucketAddress)).isEqualTo(0);

        assertThat(bucketBufferArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET + 2 * getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketBufferArray.getBucketFillCount(newBucketAddress)).isEqualTo(2);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(2);

        final int blockLength = bucketBufferArray.getBlockLength();
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();

        assertThat(bucketBufferArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset)).isTrue();
        keyHandler.theKey = 10;
        assertThat(bucketBufferArray.keyEquals(keyHandler, newBucketAddress, newBlockOffset + blockLength)).isTrue();

        bucketBufferArray.readKey(keyHandler, newBucketAddress, newBlockOffset + blockLength);
        assertThat(keyHandler.theKey).isEqualTo(10);

        bucketBufferArray.readValue(valueHandler, newBucketAddress, newBlockOffset + blockLength);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);

        bucketBufferArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(11);

        bucketBufferArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xAF);
    }

    @Test
    public void shouldNotRelocateBlocksFromBucketIfNotFit()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();

        final long firstBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(2, 1);

        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketBufferArray.addBlock(firstBucketAddress, keyHandler, valueHandler);

        keyHandler.theKey = 11;
        valueHandler.theValue = 0xAF;
        bucketBufferArray.addBlock(firstBucketAddress, keyHandler, valueHandler);

        keyHandler.theKey = 12;
        valueHandler.theValue = 0xAB;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        // expect
        expectedException.expectMessage("Blocks can't be relocate from bucket " + firstBucketAddress + " to bucket " + newBucketAddress + ". Not enough space on destination bucket.");
        expectedException.expect(IllegalArgumentException.class);

        // when
        bucketBufferArray.relocateBlocksFromBucket(firstBucketAddress, newBucketAddress);
    }

    @Test
    public void shouldOverflowBucket()
    {
        // given
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        // when
        final long overflowBucketAddress = bucketBufferArray.overflow(newBucketAddress);

        // then
        assertThat(bucketBufferArray.getBucketOverflowPointer(newBucketAddress)).isEqualTo(overflowBucketAddress);

        assertThat(bucketBufferArray.getBucketOverflowPointer(overflowBucketAddress)).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(2);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketDepth(overflowBucketAddress)).isEqualTo(OVERFLOW_BUCKET);
        assertThat(bucketBufferArray.getBucketId(overflowBucketAddress)).isEqualTo(1);
    }

    @Test
    public void shouldUseOverflowBucketIfAddBlockOnFullBucket()
    {
        // given
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);
        final long overflowBucketAddress = bucketBufferArray.overflow(newBucketAddress);

        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();

        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        keyHandler.theKey = 11;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        // when
        keyHandler.theKey = 12;
        valueHandler.theValue = 0xFF;
        final boolean wasAdded = bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();

        // then
        assertThat(wasAdded).isTrue();
        assertThat(newBlockOffset).isEqualTo(BUCKET_DATA_OFFSET);

        assertThat(bucketBufferArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET + 2 * getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketBufferArray.getBucketFillCount(newBucketAddress)).isEqualTo(2); // full

        assertThat(bucketBufferArray.getBucketLength(overflowBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET + getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketBufferArray.getBucketFillCount(overflowBucketAddress)).isEqualTo(1);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(3);

        final boolean keyEquals = bucketBufferArray.keyEquals(keyHandler, overflowBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();

        bucketBufferArray.readKey(keyHandler, overflowBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(12);

        bucketBufferArray.readValue(valueHandler, overflowBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);
    }


    @Test
    public void shouldAddBlockOnOverflowBucket()
    {
        // given
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);
        final long overflowBucketAddress = bucketBufferArray.overflow(newBucketAddress);
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();

        // when
        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        final boolean wasAdded = bucketBufferArray.addBlock(overflowBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();

        // then
        assertThat(wasAdded).isTrue();
        assertThat(newBlockOffset).isEqualTo(BUCKET_DATA_OFFSET);

        assertThat(bucketBufferArray.getBucketLength(newBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET);
        assertThat(bucketBufferArray.getBucketFillCount(newBucketAddress)).isEqualTo(0);

        assertThat(bucketBufferArray.getBucketLength(overflowBucketAddress)).isEqualTo(BUCKET_DATA_OFFSET + getBlockLength(MAX_KEY_LEN, MAX_VALUE_LEN));
        assertThat(bucketBufferArray.getBucketFillCount(overflowBucketAddress)).isEqualTo(1);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(1);

        final boolean keyEquals = bucketBufferArray.keyEquals(keyHandler, overflowBucketAddress, newBlockOffset);
        assertThat(keyEquals).isTrue();

        bucketBufferArray.readKey(keyHandler, overflowBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10);

        bucketBufferArray.readValue(valueHandler, overflowBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFF);
    }

    @Test
    public void shouldOverflowOverflowBucket()
    {
        // given
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);
        final long overflowBucketAddress = bucketBufferArray.overflow(newBucketAddress);

        // when
        final long newOverflowBucketAddress = bucketBufferArray.overflow(newBucketAddress);

        // then
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(3);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketOverflowPointer(newBucketAddress)).isEqualTo(overflowBucketAddress);

        assertThat(bucketBufferArray.getBucketDepth(overflowBucketAddress)).isEqualTo(OVERFLOW_BUCKET);
        assertThat(bucketBufferArray.getBucketId(overflowBucketAddress)).isEqualTo(1);
        assertThat(bucketBufferArray.getBucketOverflowPointer(overflowBucketAddress)).isEqualTo(newOverflowBucketAddress);

        assertThat(bucketBufferArray.getBucketDepth(newOverflowBucketAddress)).isEqualTo(OVERFLOW_BUCKET);
        assertThat(bucketBufferArray.getBucketId(newOverflowBucketAddress)).isEqualTo(1);
        assertThat(bucketBufferArray.getBucketOverflowPointer(newOverflowBucketAddress)).isEqualTo(0);
    }

    @Test
    public void shouldRemoveOverflowBucketInBetween()
    {
        // given
        final long bucketAddress = bucketBufferArray.allocateNewBucket(1, 1);
        final long firstOverflowBucketAddress = bucketBufferArray.overflow(bucketAddress);
        final long secondOverflowBucketAddress = bucketBufferArray.overflow(bucketAddress);
        final long thirdOverflowBucketAddress = bucketBufferArray.overflow(bucketAddress);

        // when
        bucketBufferArray.removeOverflowBucket(bucketAddress, secondOverflowBucketAddress);

        // then
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(4);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);

        assertThat(bucketBufferArray.getBucketOverflowPointer(bucketAddress)).isEqualTo(firstOverflowBucketAddress);
        assertThat(bucketBufferArray.getBucketOverflowPointer(firstOverflowBucketAddress)).isEqualTo(thirdOverflowBucketAddress);
        assertThat(bucketBufferArray.getBucketOverflowPointer(secondOverflowBucketAddress)).isEqualTo(0);
    }

    @Test
    public void shouldNotFailOnRemoveNotExistingOverflowBucket()
    {
        // given
        final long bucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        // when
        bucketBufferArray.removeOverflowBucket(bucketAddress, 22131);

        // then
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(1);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);

        assertThat(bucketBufferArray.getBucketOverflowPointer(bucketAddress)).isEqualTo(0);
    }

    @Test
    public void shouldNotFailOnRemoveOverflowBucketTwice()
    {
        // given
        final long bucketAddress = bucketBufferArray.allocateNewBucket(1, 1);
        final long firstOverflowBucketAddress = bucketBufferArray.overflow(bucketAddress);
        final long secondOverflowBucketAddress = bucketBufferArray.overflow(bucketAddress);
        final long thirdOverflowBucketAddress = bucketBufferArray.overflow(bucketAddress);

        // when
        bucketBufferArray.removeOverflowBucket(bucketAddress, secondOverflowBucketAddress);
        bucketBufferArray.removeOverflowBucket(bucketAddress, secondOverflowBucketAddress);

        // then
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(4);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);

        assertThat(bucketBufferArray.getBucketOverflowPointer(bucketAddress)).isEqualTo(firstOverflowBucketAddress);
        assertThat(bucketBufferArray.getBucketOverflowPointer(firstOverflowBucketAddress)).isEqualTo(thirdOverflowBucketAddress);
    }

    @Test
    public void shouldCalculateOverflowBucketCount()
    {
        // given
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);
        bucketBufferArray.overflow(newBucketAddress);
        bucketBufferArray.overflow(newBucketAddress);

        // when
        final int bucketOverflowCount = bucketBufferArray.getBucketOverflowCount(newBucketAddress);

        // then
        assertThat(bucketOverflowCount).isEqualTo(2);
    }

    @Test
    public void shouldCalculateLoadFactor()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();

        // when
        int blockCount = 0;

        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        for (int i = 0; i < 1000; i++)
        {
            final float expectedLoadFactor = getExpectedLoadFactor(blockCount, i);

            assertThat(bucketBufferArray.getLoadFactor()).isEqualTo(expectedLoadFactor);
            final long newBucketAddress = bucketBufferArray.allocateNewBucket(i, i);

            bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
            blockCount++;
            assertThat(bucketBufferArray.getLoadFactor())
                .isEqualTo(getExpectedLoadFactor(blockCount, i + 1));

            bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
            blockCount++;
            assertThat(bucketBufferArray.getLoadFactor())
                .isEqualTo(getExpectedLoadFactor(blockCount, i + 1));
        }
    }


    @Test
    public void shouldNotRemoveNotEmptyBucket()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();

        final long firstBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketBufferArray.addBlock(firstBucketAddress, keyHandler, valueHandler);

        // expect
        expectedException.expectMessage("Bucket can't be removed, since it is not empty!");
        expectedException.expect(IllegalStateException.class);


        // when
        bucketBufferArray.removeBucket(firstBucketAddress);
    }

    @Test
    public void shouldRemoveLastExistingBucket()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();

        final long firstBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;
        bucketBufferArray.addBlock(firstBucketAddress, keyHandler, valueHandler);

        // when
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();
        bucketBufferArray.removeBlock(firstBucketAddress, newBlockOffset);
        final long nextRemovableBucket = bucketBufferArray.removeBucket(firstBucketAddress);

        // then
        assertThat(nextRemovableBucket).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(1);

        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount(0)).isEqualTo(0);
    }

    @Test
    public void shouldNotRemoveBucketIfNoWasAllocated()
    {
        // given

        // expect
        expectedException.expectMessage("No bucket in buffer 0 on offset 4");
        expectedException.expect(IllegalArgumentException.class);

        // when
        bucketBufferArray.removeBucket(4L);
    }

    @Test
    public void shouldReturnTrueIfIsRemovableBucket()
    {
        // given

        // when
        final long firstBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        // then
        assertThat(bucketBufferArray.isBucketRemovable(firstBucketAddress)).isTrue();
    }

    @Test
    public void shouldReturnTrueIfIsRemovableBucketAfterOtherBucketsAreRemoved()
    {
        // given
        final long firstBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        final List<Long> bucketAddresses = new ArrayList<>();
        for (int i = 1; i < 32; i++)
        {
            bucketAddresses.add(bucketBufferArray.allocateNewBucket(i, i));
        }

        // when
        Collections.reverse(bucketAddresses);
        for (Long addr : bucketAddresses)
        {
            bucketBufferArray.removeBucket(addr);
        }

        // then
        assertThat(bucketBufferArray.isBucketRemovable(firstBucketAddress)).isTrue();
    }

    @Test
    public void shouldReturnFalseIfIsRemovableBucketAfterOtherBucketsAreRemovedButHasOverflowBucketInOtherBuffer()
    {
        // given
        final long firstBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

        final List<Long> bucketAddresses = new ArrayList<>();
        for (int i = 1; i < 32; i++)
        {
            bucketAddresses.add(bucketBufferArray.allocateNewBucket(i, i));
        }
        bucketBufferArray.overflow(firstBucketAddress);

        // when
        Collections.reverse(bucketAddresses);
        for (Long addr : bucketAddresses)
        {
            bucketBufferArray.removeBucket(addr);
        }

        // then
        assertThat(bucketBufferArray.isBucketRemovable(firstBucketAddress)).isFalse();
    }

    @Test
    public void shouldRemoveIfLastBucket()
    {
        // given
        final long firstBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);
        final long lastBucketAddress = bucketBufferArray.allocateNewBucket(2, 2);

        // when
        final long nextRemovableBucket = bucketBufferArray.removeBucket(lastBucketAddress);

        // then
        assertThat(nextRemovableBucket).isEqualTo(firstBucketAddress);
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(1);

        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(1);
        assertThat(bucketBufferArray.getBucketCount(0)).isEqualTo(1);
    }

    @Test
    public void shouldNotRemoveIfNotLastBucket()
    {
        // give
        final long firstBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);
        final long lastBucketAddress = bucketBufferArray.allocateNewBucket(2, 2);

        // when
        final long nextRemovableBucket = bucketBufferArray.removeBucket(firstBucketAddress);

        // then
        assertThat(nextRemovableBucket).isEqualTo(lastBucketAddress);
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(1);

        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(2);
        assertThat(bucketBufferArray.getBucketCount(0)).isEqualTo(2);
    }

    @Test
    public void shouldReleaseBucketBufferIfEmptyAfterRemoveBucket()
    {
        // given
        allocateBuckets(32);
        final long firstBucketAddress = bucketBufferArray.allocateNewBucket(32, 32);

        // when
        final long nextRemovableBucket = bucketBufferArray.removeBucket(firstBucketAddress);

        // then
        final int lastBucketOffsetInFirstBuffer = BUCKET_BUFFER_HEADER_LENGTH + 31 * bucketBufferArray.getMaxBucketLength();
        assertThat(nextRemovableBucket)
            .isEqualTo(getBucketAddress(0, lastBucketOffsetInFirstBuffer));

        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(1);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(32);
        assertThat(bucketBufferArray.getBucketCount(0)).isEqualTo(32);
    }


    @Test
    public void shouldNotReleaseBucketBufferIfNotEmptyAfterRemoveBucket()
    {
        // given
        allocateBuckets(33);
        final long firstBucketAddress = bucketBufferArray.allocateNewBucket(33, 33);

        // when
        final long nextRemovableBucket = bucketBufferArray.removeBucket(firstBucketAddress);

        // then
        assertThat(nextRemovableBucket)
            .isEqualTo(getBucketAddress(1, BUCKET_BUFFER_HEADER_LENGTH));

        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(2);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(33);
        assertThat(bucketBufferArray.getBucketCount(0)).isEqualTo(32);
        assertThat(bucketBufferArray.getBucketCount(1)).isEqualTo(1);
    }

    @Test
    public void shouldNotReleaseBucketBufferInBetween()
    {

        // given
        allocateBuckets(65);

        // when
        for (int i = 31; i >= 0; i--)
        {
            final int bufferId = 1;
            final int bucketOffset = BUCKET_BUFFER_HEADER_LENGTH + i * bucketBufferArray.getMaxBucketLength();
            bucketBufferArray.removeBucket(getBucketAddress(bufferId, bucketOffset));
        }

        //then
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(3);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(33);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount(0)).isEqualTo(32);
        assertThat(bucketBufferArray.getBucketCount(1)).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount(2)).isEqualTo(1);
    }

    @Test
    public void shouldResolveNextRemovableAddressIfBucketBufferIsNotReleased()
    {
        // given
        allocateBuckets(65);

        // when
        for (int i = 31; i >= 1; i--)
        {
            final int bufferId = 1;
            final int bucketOffset = BUCKET_BUFFER_HEADER_LENGTH + i * bucketBufferArray.getMaxBucketLength();
            bucketBufferArray.removeBucket(getBucketAddress(bufferId, bucketOffset));
        }
        final long nextRemovableBucket = bucketBufferArray.removeBucket(getBucketAddress(1, BUCKET_BUFFER_HEADER_LENGTH));

        // then
        final int lastBucketOffsetInFirstBuffer = BUCKET_BUFFER_HEADER_LENGTH + 31 * bucketBufferArray.getMaxBucketLength();
        assertThat(nextRemovableBucket)
            .isEqualTo(getBucketAddress(0, lastBucketOffsetInFirstBuffer));

        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(3);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(33);
        assertThat(bucketBufferArray.getBucketCount(0)).isEqualTo(32);
        assertThat(bucketBufferArray.getBucketCount(1)).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount(2)).isEqualTo(1);
    }


    @Test
    public void shouldResolveNextRemovableAddressIfNextEmptyBucketBufferWasReleased()
    {
        // given
        allocateBuckets(65);

        // when
        for (int i = 31; i >= 0; i--)
        {
            final int bufferId = 1;
            final int bucketOffset = BUCKET_BUFFER_HEADER_LENGTH + i * bucketBufferArray.getMaxBucketLength();
            bucketBufferArray.removeBucket(getBucketAddress(bufferId, bucketOffset));
        }
        final long nextRemovableBucket = bucketBufferArray.removeBucket(getBucketAddress(2, BUCKET_BUFFER_HEADER_LENGTH));

        // then
        final int lastBucketOffsetInFirstBuffer = BUCKET_BUFFER_HEADER_LENGTH + 31 * bucketBufferArray.getMaxBucketLength();
        assertThat(nextRemovableBucket)
            .isEqualTo(getBucketAddress(0, lastBucketOffsetInFirstBuffer));

        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(1);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(32);
        assertThat(bucketBufferArray.getBucketCount(0)).isEqualTo(32);
    }

    @Test
    public void shouldResolveNextRemovableAddressIfNextBucketBufferIsEmptyAndIsNotReleased()
    {
        // given
        allocateBuckets(97);

        // when
        // buffer 1 is cleared
        for (int i = 31; i >= 0; i--)
        {
            final int bufferId = 1;
            final int bucketOffset = BUCKET_BUFFER_HEADER_LENGTH + i * bucketBufferArray.getMaxBucketLength();
            bucketBufferArray.removeBucket(getBucketAddress(bufferId, bucketOffset));
        }
        // and buffer 2 is cleared
        for (int i = 31; i >= 1; i--)
        {
            final int bufferId = 2;
            final int bucketOffset = BUCKET_BUFFER_HEADER_LENGTH + i * bucketBufferArray.getMaxBucketLength();
            bucketBufferArray.removeBucket(getBucketAddress(bufferId, bucketOffset));
        }

        final long nextRemovableBucket = bucketBufferArray.removeBucket(getBucketAddress(2, BUCKET_BUFFER_HEADER_LENGTH));

        // then
        final int lastBucketOffsetInFirstBuffer = BUCKET_BUFFER_HEADER_LENGTH + 31 * bucketBufferArray.getMaxBucketLength();
        assertThat(nextRemovableBucket)
            .isEqualTo(getBucketAddress(0, lastBucketOffsetInFirstBuffer));

        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(4);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(33);
        assertThat(bucketBufferArray.getBucketCount(0)).isEqualTo(32);
        assertThat(bucketBufferArray.getBucketCount(1)).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount(2)).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount(3)).isEqualTo(1);
    }


    @Test
    public void shouldReleaseBucketBufferInBetweenAndResizeAddressBufferAndResolveNextRemovableAddress()
    {
        // given
        allocateBuckets(32 * 48 + 1);

        // when buffer between 0 and 32 is cleared and then last bucket buffer is cleared
        for (int bufferId = 1; bufferId < 48; bufferId++)
        {
            for (int i = 31; i >= 0; i--)
            {
                final int bucketOffset = BUCKET_BUFFER_HEADER_LENGTH + i * bucketBufferArray.getMaxBucketLength();
                bucketBufferArray.removeBucket(getBucketAddress(bufferId, bucketOffset));
            }
        }

        final long nextRemovableBucket = bucketBufferArray.removeBucket(getBucketAddress(48, BUCKET_BUFFER_HEADER_LENGTH));

        // then bucket buffers are released and address buffer is shrinked
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(1);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(32);
        assertThat(bucketBufferArray.realAddresses.length).isEqualTo(32);

        final int lastBucketOffsetInFirstBuffer = BUCKET_BUFFER_HEADER_LENGTH + 31 * bucketBufferArray.getMaxBucketLength();
        assertThat(nextRemovableBucket)
            .isEqualTo(getBucketAddress(0, lastBucketOffsetInFirstBuffer));
    }

    @Test
    public void shouldAbleToAllocateNewBufferAfterRemoveAllEmptyBucketBuffers()
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;

        allocateBuckets(63);
        final long lastBucketAddress = bucketBufferArray.allocateNewBucket(64, 64);
        bucketBufferArray.addBlock(lastBucketAddress, keyHandler, valueHandler);

        final long bucketAddressInThirdBuffer = bucketBufferArray.allocateNewBucket(64, 64);
        bucketBufferArray.addBlock(bucketAddressInThirdBuffer, keyHandler, valueHandler);

        // when
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();
        bucketBufferArray.removeBlock(lastBucketAddress, newBlockOffset);
        for (int i = 31; i >= 0; i--)
        {
            final int bufferId = 1;
            final int bucketOffset = BUCKET_BUFFER_HEADER_LENGTH + i * bucketBufferArray.getMaxBucketLength();
            bucketBufferArray.removeBucket(getBucketAddress(bufferId, bucketOffset));
        }
        bucketBufferArray.removeBlock(bucketAddressInThirdBuffer, newBlockOffset);
        bucketBufferArray.removeBucket(bucketAddressInThirdBuffer);

        // then second and third buffer is released
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(1);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(32);

        // then it is possible to allocate again a bucket which triggers allocation of new bucket buffer
        bucketBufferArray.allocateNewBucket(33, 33);
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(2);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(33);
    }

    @Test
    public void shouldAddAndRemoveBunchOfBlocks()
    {
        // given
        final List<Long> bucketAddresses = fillBucketBufferArray(32 * 32 + 1);

        // when first block and buckets are removed
        for (int i = 0; i < 32 * 32; i++)
        {
            final long bucketAddress = bucketAddresses.get(i);
            final int firstBlockOffset = bucketBufferArray.getFirstBlockOffset();
            bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset);
            bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset + bucketBufferArray.getBlockLength());
            bucketBufferArray.removeBucket(bucketAddress);
        }

        // then buckets and bucket buffers can't be freed - only the last buckets in the buffers
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(33);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(2);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(32 * 32 - 31);
        assertThat(bucketBufferArray.realAddresses.length).isEqualTo(64);

        // if last one is cleared
        final long bucketAddress = bucketAddresses.get(32 * 32);
        final int firstBlockOffset = bucketBufferArray.getFirstBlockOffset();
        bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset);
        bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset + bucketBufferArray.getBlockLength());
        bucketBufferArray.removeBucket(bucketAddress);

        // then all other can be freed as well
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(32);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(32 * 32 - 32);
        assertThat(bucketBufferArray.realAddresses.length).isEqualTo(64);
    }

    @Test
    public void shouldAddAndRemoveInBunchReverse()
    {
        // given
        final List<Long> bucketAddresses = fillBucketBufferArray(32 * 32 + 1);

        // when last block and buckets are removed
        Collections.reverse(bucketAddresses);
        for (int i = 0; i < 32 * 32; i++)
        {
            final long bucketAddress = bucketAddresses.get(i);
            final int firstBlockOffset = bucketBufferArray.getFirstBlockOffset();
            bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset);
            bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset + bucketBufferArray.getBlockLength());
            bucketBufferArray.removeBucket(bucketAddress);
        }

        // then bucket buffers are direct freed
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(1);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(2);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(1);
        assertThat(bucketBufferArray.realAddresses.length).isEqualTo(32);

        // if last one is cleared
        final long bucketAddress = bucketAddresses.get(32 * 32);
        final int firstBlockOffset = bucketBufferArray.getFirstBlockOffset();
        bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset);
        bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset + bucketBufferArray.getBlockLength());
        bucketBufferArray.removeBucket(bucketAddress);

        // then all other can be freed as well
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(1);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(0);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(0);
        assertThat(bucketBufferArray.realAddresses.length).isEqualTo(32);
    }

    @Test
    public void shouldShrinkAddressBuffer()
    {
        // given
        final List<Long> bucketAddresses = fillBucketBufferArray(32 * 32 + 1);

        // when last block is removed
        long bucketAddress = bucketAddresses.get(32 * 32);
        final int firstBlockOffset = bucketBufferArray.getFirstBlockOffset();
        bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset);
        bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset + bucketBufferArray.getBlockLength());
        bucketBufferArray.removeBucket(bucketAddress);

        // then bucket and bucket buffer is also removed
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(32);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(32 * 32 * 2);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(32 * 32);
        assertThat(bucketBufferArray.realAddresses.length).isEqualTo(64);

        // after removing block and buckets of next bucket buffer
        for (int i = (32 * 32) - 1; i >= 32 * 31; i--)
        {
            bucketAddress = bucketAddresses.get(i);
            bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset);
            bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset + bucketBufferArray.getBlockLength());
            bucketBufferArray.removeBucket(bucketAddress);
        }

        // then realAddresses is shrinked -> since 31 (buffer count) is less then half of address buffer length (=64)
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(31);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(32 * 31 * 2);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(32 * 31);
        assertThat(bucketBufferArray.realAddresses.length).isEqualTo(32);
    }

    @Test
    public void shouldNotUpdateNextNotFilledBucketBufferIdIfBucketBufferIsLarger()
    {
        // given
        final List<Long> bucketAddresses = fillBucketBufferArray(3 * 32);

        // when bucket in second buffer is removed
        Long bucketAddress = bucketAddresses.get(63);
        final int firstBlockOffset = bucketBufferArray.getFirstBlockOffset();
        bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset);
        bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset + bucketBufferArray.getBlockLength());
        bucketBufferArray.removeBucket(bucketAddress);

        // then
        assertThat(bucketBufferArray.getBucketCount(1)).isEqualTo(31);
        assertThat(bucketBufferArray.nextNotFullBucketBuffer).isEqualTo(1);

        // when bucket in third buffer is removed
        bucketAddress = bucketAddresses.get(95);
        bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset);
        bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset + bucketBufferArray.getBlockLength());
        bucketBufferArray.removeBucket(bucketAddress);

        // then first not full bucket buffer is not updated
        assertThat(bucketBufferArray.getBucketCount(2)).isEqualTo(31);
        assertThat(bucketBufferArray.nextNotFullBucketBuffer).isEqualTo(1);

        // when new bucket is allocated
        bucketBufferArray.allocateNewBucket(32 + 16, 21);

        // then bucket is allocated in second bucket and first not full bucket buffer id is updated
        assertThat(bucketBufferArray.getBucketCount(1)).isEqualTo(32);
        assertThat(bucketBufferArray.nextNotFullBucketBuffer).isEqualTo(2);
    }

    @Test
    public void shouldUpdateNextNotFilledBucketBufferId()
    {
        // given
        final List<Long> bucketAddresses = fillBucketBufferArray(3 * 32);

        // when bucket in second buffer is removed
        Long bucketAddress = bucketAddresses.get(95);
        final int firstBlockOffset = bucketBufferArray.getFirstBlockOffset();
        bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset);
        bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset + bucketBufferArray.getBlockLength());
        bucketBufferArray.removeBucket(bucketAddress);

        // then
        assertThat(bucketBufferArray.getBucketCount(2)).isEqualTo(31);
        assertThat(bucketBufferArray.nextNotFullBucketBuffer).isEqualTo(2);

        // when bucket in third buffer is removed
        bucketAddress = bucketAddresses.get(63);
        bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset);
        bucketBufferArray.removeBlock(bucketAddress, firstBlockOffset + bucketBufferArray.getBlockLength());
        bucketBufferArray.removeBucket(bucketAddress);

        // then last not full bucket buffer is updated
        assertThat(bucketBufferArray.getBucketCount(1)).isEqualTo(31);
        assertThat(bucketBufferArray.nextNotFullBucketBuffer).isEqualTo(1);

        // when new bucket is allocated
        bucketBufferArray.allocateNewBucket(32 + 16, 21);

        // then bucket is allocated in second bucket and last not full bucket buffer id is updated
        assertThat(bucketBufferArray.getBucketCount(1)).isEqualTo(32);
        assertThat(bucketBufferArray.nextNotFullBucketBuffer).isEqualTo(2);
    }

    @Test
    public void shouldUpdateNextNotFilledBucketBufferIdOnNewBucketBufferAllocation()
    {
        // given
        fillBucketBufferArray(32);

        // when new bucket is allocated
        bucketBufferArray.allocateNewBucket(32, 32);

        // then bucket is allocated in second bucket and last not full bucket buffer id is updated
        assertThat(bucketBufferArray.getBucketCount(1)).isEqualTo(1);
        assertThat(bucketBufferArray.nextNotFullBucketBuffer).isEqualTo(1);
    }

    @Test
    public void shouldNextNotFilledBucketBufferIdStartOnZero()
    {
        // given
        assertThat(bucketBufferArray.nextNotFullBucketBuffer).isEqualTo(0);

        // when new bucket is allocated
        bucketBufferArray.allocateNewBucket(1, 1);

        // then bucket is allocated in first bucket and last not full bucket buffer id is not updated since bucket buffer is not full
        assertThat(bucketBufferArray.getBucketCount(0)).isEqualTo(1);
        assertThat(bucketBufferArray.nextNotFullBucketBuffer).isEqualTo(0);
    }

    @Test
    public void shouldResetNextNotFilledBucketBufferIdOnClear()
    {
        // given
        fillBucketBufferArray(32 * 32);

        // when
        bucketBufferArray.clear();

        // then
        assertThat(bucketBufferArray.getBucketCount(0)).isEqualTo(0);
        assertThat(bucketBufferArray.nextNotFullBucketBuffer).isEqualTo(0);
    }

    private float getExpectedLoadFactor(float blockCount, int bucketCount)
    {
        return bucketCount == 0
            ? 0
            : blockCount / ((float) bucketCount * 2);
    }

    private void allocateBuckets(int bucketCount)
    {
        for (int i = 0; i < bucketCount; i++)
        {
            bucketBufferArray.allocateNewBucket(i, i);
        }
    }

    private List<Long> fillBucketBufferArray(int bucketCount)
    {
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        keyHandler.theKey = 10;
        valueHandler.theValue = 0xFF;

        final List<Long> bucketAddresses = new ArrayList<>();
        long lastBucketAddress;
        for (int i = 0; i < bucketCount; i++)
        {
            lastBucketAddress = bucketBufferArray.allocateNewBucket(i, i);
            bucketBufferArray.addBlock(lastBucketAddress, keyHandler, valueHandler);
            bucketBufferArray.addBlock(lastBucketAddress, keyHandler, valueHandler);
            bucketAddresses.add(lastBucketAddress);
        }
        return bucketAddresses;
    }
}

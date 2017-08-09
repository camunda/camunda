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

import static io.zeebe.map.BucketBufferArray.ALLOCATION_FACTOR;
import static io.zeebe.map.BucketBufferArray.getBucketAddress;
import static io.zeebe.map.BucketBufferArrayDescriptor.*;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import java.io.*;

import io.zeebe.map.types.LongKeyHandler;
import io.zeebe.map.types.LongValueHandler;
import io.zeebe.test.util.io.AlwaysFailingInputStream;
import io.zeebe.test.util.io.RepeatedlyFailingInputStream;
import io.zeebe.test.util.io.RepeatedlyFailingOutputStream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 *
 */
public class BucketBufferArrayIOTest
{
    private static final int MAX_KEY_LEN = SIZE_OF_LONG;
    private static final int MAX_VALUE_LEN = SIZE_OF_LONG;
    private static final int MIN_BLOCK_COUNT = 2;

    private static final int DATA_COUNT = 100_000;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private BucketBufferArray bucketBufferArray;

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
    public void shouldWriteBucketArrayToStream() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(3, 3);

        keyHandler.theKey = 10L;
        valueHandler.theValue = 0xFFL;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        bucketBufferArray.getFirstBlockOffset();

        // when
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[16];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);

        // then
        final byte[] writtenBytes = outputStream.toByteArray();
        assertThat(writtenBytes.length).isEqualTo((int) bucketBufferArray.size());
        final UnsafeBuffer buffer = new UnsafeBuffer(writtenBytes);

        // bucket buffer main header
        assertThat(buffer.getInt(MAIN_BUFFER_COUNT_OFFSET)).isEqualTo(1);
        assertThat(buffer.getInt(MAIN_BUCKET_COUNT_OFFSET)).isEqualTo(1);
        assertThat(buffer.getLong(MAIN_BLOCK_COUNT_OFFSET)).isEqualTo(1L);

        // first bucket buffer
        assertThat(buffer.getInt(MAIN_BUCKET_BUFFER_HEADER_LEN + BUCKET_BUFFER_BUCKET_COUNT_OFFSET)).isEqualTo(1);

        // first bucket
        final int bucketOffset = MAIN_BUCKET_BUFFER_HEADER_LEN + BUCKET_BUFFER_HEADER_LENGTH;
        assertThat(buffer.getInt(bucketOffset + BUCKET_FILL_COUNT_OFFSET)).isEqualTo(1);
        assertThat(buffer.getInt(bucketOffset + BUCKET_ID_OFFSET)).isEqualTo(3);
        assertThat(buffer.getInt(bucketOffset + BUCKET_DEPTH_OFFSET)).isEqualTo(3);
        assertThat(buffer.getLong(bucketOffset + BUCKET_OVERFLOW_POINTER_OFFSET)).isEqualTo(0L);

        // first block
        final int blockOffset = MAIN_BUCKET_BUFFER_HEADER_LEN + BUCKET_BUFFER_HEADER_LENGTH + BUCKET_DATA_OFFSET;
        assertThat(buffer.getLong(blockOffset + BLOCK_KEY_OFFSET)).isEqualTo(10L);
        assertThat(buffer.getLong((int) getBlockValueOffset(blockOffset, SIZE_OF_LONG))).isEqualTo(0xFFL);
    }

    @Test
    public void shouldWriteWithSmallBuffer() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(3, 3);

        keyHandler.theKey = 10L;
        valueHandler.theValue = 0xFFL;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        bucketBufferArray.getFirstBlockOffset();

        // when
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[1];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);

        // then
        final byte[] writtenBytes = outputStream.toByteArray();
        assertThat(writtenBytes.length).isEqualTo((int) bucketBufferArray.size());
        final UnsafeBuffer buffer = new UnsafeBuffer(writtenBytes);

        // bucket buffer main header
        assertThat(buffer.getInt(MAIN_BUFFER_COUNT_OFFSET)).isEqualTo(1);
        assertThat(buffer.getInt(MAIN_BUCKET_COUNT_OFFSET)).isEqualTo(1);
        assertThat(buffer.getLong(MAIN_BLOCK_COUNT_OFFSET)).isEqualTo(1L);

        // first bucket buffer
        assertThat(buffer.getInt(MAIN_BUCKET_BUFFER_HEADER_LEN + BUCKET_BUFFER_BUCKET_COUNT_OFFSET)).isEqualTo(1);

        // first bucket
        final int bucketOffset = MAIN_BUCKET_BUFFER_HEADER_LEN + BUCKET_BUFFER_HEADER_LENGTH;
        assertThat(buffer.getInt(bucketOffset + BUCKET_FILL_COUNT_OFFSET)).isEqualTo(1);
        assertThat(buffer.getInt(bucketOffset + BUCKET_ID_OFFSET)).isEqualTo(3);
        assertThat(buffer.getInt(bucketOffset + BUCKET_DEPTH_OFFSET)).isEqualTo(3);
        assertThat(buffer.getLong(bucketOffset + BUCKET_OVERFLOW_POINTER_OFFSET)).isEqualTo(0L);

        // first block
        final int blockOffset = MAIN_BUCKET_BUFFER_HEADER_LEN + BUCKET_BUFFER_HEADER_LENGTH + BUCKET_DATA_OFFSET;
        assertThat(buffer.getLong(blockOffset + BLOCK_KEY_OFFSET)).isEqualTo(10L);
        assertThat(buffer.getLong((int) getBlockValueOffset(blockOffset, SIZE_OF_LONG))).isEqualTo(0xFFL);
    }

    @Test
    public void shouldWriteWithLargeBuffer() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(3, 3);

        keyHandler.theKey = 10L;
        valueHandler.theValue = 0xFFL;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        bucketBufferArray.getFirstBlockOffset();

        // when
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[4096];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);

        // then
        final byte[] writtenBytes = outputStream.toByteArray();
        assertThat(writtenBytes.length).isEqualTo((int) bucketBufferArray.size());
        final UnsafeBuffer buffer = new UnsafeBuffer(writtenBytes);

        // bucket buffer main header
        assertThat(buffer.getInt(MAIN_BUFFER_COUNT_OFFSET)).isEqualTo(1);
        assertThat(buffer.getInt(MAIN_BUCKET_COUNT_OFFSET)).isEqualTo(1);
        assertThat(buffer.getLong(MAIN_BLOCK_COUNT_OFFSET)).isEqualTo(1L);

        // first bucket buffer
        assertThat(buffer.getInt(MAIN_BUCKET_BUFFER_HEADER_LEN + BUCKET_BUFFER_BUCKET_COUNT_OFFSET)).isEqualTo(1);

        // first bucket
        final int bucketOffset = MAIN_BUCKET_BUFFER_HEADER_LEN + BUCKET_BUFFER_HEADER_LENGTH;
        assertThat(buffer.getInt(bucketOffset + BUCKET_FILL_COUNT_OFFSET)).isEqualTo(1);
        assertThat(buffer.getInt(bucketOffset + BUCKET_ID_OFFSET)).isEqualTo(3);
        assertThat(buffer.getInt(bucketOffset + BUCKET_DEPTH_OFFSET)).isEqualTo(3);
        assertThat(buffer.getLong(bucketOffset + BUCKET_OVERFLOW_POINTER_OFFSET)).isEqualTo(0L);

        // first block
        final int blockOffset = MAIN_BUCKET_BUFFER_HEADER_LEN + BUCKET_BUFFER_HEADER_LENGTH + BUCKET_DATA_OFFSET;
        assertThat(buffer.getLong(blockOffset + BLOCK_KEY_OFFSET)).isEqualTo(10L);
        assertThat(buffer.getLong((int) getBlockValueOffset(blockOffset, SIZE_OF_LONG))).isEqualTo(0xFFL);
    }

    @Test
    public void shouldWriteLargeDataSet() throws IOException
    {
        // given filled bucket buffer array
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final int blockCountPerBucket = 16;
        final int blockSize = 16;
        final int bucketBuffers = (int) Math.ceil(Math.ceil((double) DATA_COUNT / (double) ALLOCATION_FACTOR) / (double) blockCountPerBucket);
        final BucketBufferArray bucketBufferArray = createFilledBucketBufferArray(keyHandler, valueHandler, blockCountPerBucket, bucketBuffers);

        // when
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[16];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);

        // then
        final byte[] writtenBytes = outputStream.toByteArray();
        assertThat(writtenBytes.length).isEqualTo((int) bucketBufferArray.size());

        // bucket buffer main header
        final UnsafeBuffer buffer = new UnsafeBuffer(writtenBytes);

        assertThat(buffer.getInt(MAIN_BUFFER_COUNT_OFFSET)).isEqualTo(bucketBuffers);
        assertThat(buffer.getInt(MAIN_BUCKET_COUNT_OFFSET)).isEqualTo(bucketBuffers * ALLOCATION_FACTOR);
        assertThat(buffer.getLong(MAIN_BLOCK_COUNT_OFFSET)).isEqualTo(bucketBuffers * ALLOCATION_FACTOR * blockCountPerBucket);


        for (int bucketBufferIndex = 0; bucketBufferIndex < bucketBuffers; bucketBufferIndex++)
        {
            int indexBase = MAIN_BUCKET_BUFFER_HEADER_LEN + bucketBufferIndex * bucketBufferArray.getMaxBucketBufferLength();
            assertThat(buffer.getInt(indexBase + BUCKET_BUFFER_BUCKET_COUNT_OFFSET)).isEqualTo(32);

            for (int bucketIndex = 0; bucketIndex < ALLOCATION_FACTOR; bucketIndex++)
            {
                indexBase = MAIN_BUCKET_BUFFER_HEADER_LEN + bucketBufferIndex * bucketBufferArray.getMaxBucketBufferLength() + BUCKET_BUFFER_HEADER_LENGTH +
                    bucketIndex * bucketBufferArray.getMaxBucketLength();

                assertThat(buffer.getInt(indexBase + BUCKET_FILL_COUNT_OFFSET)).isEqualTo(blockCountPerBucket);
                assertThat(buffer.getInt(indexBase + BUCKET_ID_OFFSET)).isEqualTo(bucketBufferIndex + bucketIndex);
                assertThat(buffer.getInt(indexBase + BUCKET_DEPTH_OFFSET)).isEqualTo(bucketBufferIndex + bucketIndex + 1);
                assertThat(buffer.getLong(indexBase + BUCKET_OVERFLOW_POINTER_OFFSET)).isEqualTo(0);

                keyHandler.theKey = 10L;
                valueHandler.theValue = 0xFFL;
                for (int blockIndex = 0; blockIndex < 16; blockIndex++)
                {
                    indexBase = MAIN_BUCKET_BUFFER_HEADER_LEN +
                        bucketBufferIndex * bucketBufferArray.getMaxBucketBufferLength() +
                        BUCKET_BUFFER_HEADER_LENGTH +
                        bucketIndex * bucketBufferArray.getMaxBucketLength() +
                        BUCKET_HEADER_LENGTH +
                        blockIndex * blockSize;

                    assertThat(buffer.getLong(indexBase)).isEqualTo(10L + bucketBufferIndex * bucketIndex * blockIndex);
                    assertThat(buffer.getLong(indexBase + SIZE_OF_LONG)).isEqualTo(0xFFL + bucketBufferIndex * bucketIndex * blockIndex);
                }
            }
        }

        // finally
        bucketBufferArray.close();
    }

    @Test
    public void shouldThrowIOExceptionOnWriteIfStreamIsClosed() throws IOException
    {
        // given
        final File tmpFile = File.createTempFile("tmpFile", ".tmp");
        final FileOutputStream outputStream = new FileOutputStream(tmpFile);
        outputStream.close();
        tmpFile.delete();

        // expect IOException
        expectedException.expect(IOException.class);
        expectedException.expectMessage("Stream Closed");

        // when
        final byte[] writeBuffer = new byte[16];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);
    }


    @Test
    public void shouldThrowIOExceptionOnWrite() throws IOException
    {
        // given
        final RepeatedlyFailingOutputStream outputStream = new RepeatedlyFailingOutputStream(new ByteArrayOutputStream());

        // expect IOException
        expectedException.expect(IOException.class);
        expectedException.expectMessage("Write failure");

        // when
        final byte[] writeBuffer = new byte[16];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);
    }

    @Test
    public void shouldWriteWithFullBucketBuffers() throws IOException
    {
        // given filled bucket buffer array
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final int blockCountPerBucket = 16;
        final int blockSize = 16;
        // initial realAddresses length is 32
        final int bucketBuffers = 32;
        // all bucket buffers will be filled
        final BucketBufferArray bucketBufferArray = createFilledBucketBufferArray(keyHandler, valueHandler, blockCountPerBucket, bucketBuffers);

        // when
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[16];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);

        // then
        final byte[] writtenBytes = outputStream.toByteArray();
        assertThat(writtenBytes.length).isEqualTo((int) bucketBufferArray.size());

        // bucket buffer main header
        final UnsafeBuffer buffer = new UnsafeBuffer(writtenBytes);

        assertThat(buffer.getInt(MAIN_BUFFER_COUNT_OFFSET)).isEqualTo(bucketBuffers);
        assertThat(buffer.getInt(MAIN_BUCKET_COUNT_OFFSET)).isEqualTo(bucketBuffers * ALLOCATION_FACTOR);
        assertThat(buffer.getLong(MAIN_BLOCK_COUNT_OFFSET)).isEqualTo(bucketBuffers * ALLOCATION_FACTOR * blockCountPerBucket);


        for (int bucketBufferIndex = 0; bucketBufferIndex < bucketBuffers; bucketBufferIndex++)
        {
            int indexBase = MAIN_BUCKET_BUFFER_HEADER_LEN + bucketBufferIndex * bucketBufferArray.getMaxBucketBufferLength();
            assertThat(buffer.getInt(indexBase + BUCKET_BUFFER_BUCKET_COUNT_OFFSET)).isEqualTo(32);

            for (int bucketIndex = 0; bucketIndex < ALLOCATION_FACTOR; bucketIndex++)
            {
                indexBase = MAIN_BUCKET_BUFFER_HEADER_LEN + bucketBufferIndex * bucketBufferArray.getMaxBucketBufferLength() + BUCKET_BUFFER_HEADER_LENGTH +
                    bucketIndex * bucketBufferArray.getMaxBucketLength();

                assertThat(buffer.getInt(indexBase + BUCKET_FILL_COUNT_OFFSET)).isEqualTo(blockCountPerBucket);
                assertThat(buffer.getInt(indexBase + BUCKET_ID_OFFSET)).isEqualTo(bucketBufferIndex + bucketIndex);
                assertThat(buffer.getInt(indexBase + BUCKET_DEPTH_OFFSET)).isEqualTo(bucketBufferIndex + bucketIndex + 1);
                assertThat(buffer.getLong(indexBase + BUCKET_OVERFLOW_POINTER_OFFSET)).isEqualTo(0);

                keyHandler.theKey = 10L;
                valueHandler.theValue = 0xFFL;
                for (int blockIndex = 0; blockIndex < 16; blockIndex++)
                {
                    indexBase = MAIN_BUCKET_BUFFER_HEADER_LEN +
                        bucketBufferIndex * bucketBufferArray.getMaxBucketBufferLength() +
                        BUCKET_BUFFER_HEADER_LENGTH +
                        bucketIndex * bucketBufferArray.getMaxBucketLength() +
                        BUCKET_HEADER_LENGTH +
                        blockIndex * blockSize;

                    assertThat(buffer.getLong(indexBase)).isEqualTo(10L + bucketBufferIndex * bucketIndex * blockIndex);
                    assertThat(buffer.getLong(indexBase + SIZE_OF_LONG)).isEqualTo(0xFFL + bucketBufferIndex * bucketIndex * blockIndex);
                }
            }
        }

        // finally
        bucketBufferArray.close();
    }


    @Test
    public void shouldWriteLargeBucketBufferArray() throws IOException
    {
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        for (int i = 0; i < ALLOCATION_FACTOR + 1; i++)
        {
            final long newBucketAddress = bucketBufferArray.allocateNewBucket(i, i + 1);
            keyHandler.theKey = 10 * (i + 1);
            valueHandler.theValue = 0xFF * (i + 1);
            bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        }

        // when
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[16];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);

        // then
        final byte[] writtenBytes = outputStream.toByteArray();
        assertThat(writtenBytes.length).isEqualTo((int) bucketBufferArray.size());

        final UnsafeBuffer buffer = new UnsafeBuffer(writtenBytes);

        // bucket buffer main header
        assertThat(buffer.getInt(MAIN_BUFFER_COUNT_OFFSET)).isEqualTo(2);
        assertThat(buffer.getInt(MAIN_BUCKET_COUNT_OFFSET)).isEqualTo(33);
        assertThat(buffer.getLong(MAIN_BLOCK_COUNT_OFFSET)).isEqualTo(33);

        // first bucket buffer
        assertThat(buffer.getInt(MAIN_BUCKET_BUFFER_HEADER_LEN + BUCKET_BUFFER_BUCKET_COUNT_OFFSET)).isEqualTo(32);

        for (int i = 0; i < ALLOCATION_FACTOR; i++)
        {
            final int indexBase = MAIN_BUCKET_BUFFER_HEADER_LEN +
                BUCKET_BUFFER_HEADER_LENGTH +
                i * bucketBufferArray.getMaxBucketLength();

            // first bucket
            assertThat(buffer.getInt(indexBase + BUCKET_ID_OFFSET)).isEqualTo(i);
            assertThat(buffer.getInt(indexBase + BUCKET_DEPTH_OFFSET)).isEqualTo(i + 1);
            assertThat(buffer.getInt(indexBase + BUCKET_FILL_COUNT_OFFSET)).isEqualTo(1);

            // first block
            assertThat(buffer.getInt(indexBase + BUCKET_DATA_OFFSET)).isEqualTo(10 * (i + 1));
            assertThat(buffer.getInt(indexBase + BUCKET_DATA_OFFSET + SIZE_OF_LONG)).isEqualTo(0xFF * (i + 1));
        }

        int indexBase = MAIN_BUCKET_BUFFER_HEADER_LEN + bucketBufferArray.getMaxBucketBufferLength();
        assertThat(buffer.getInt(indexBase + BUCKET_BUFFER_BUCKET_COUNT_OFFSET)).isEqualTo(1);
        indexBase += BUCKET_BUFFER_HEADER_LENGTH;

        assertThat(buffer.getInt(indexBase + BUCKET_FILL_COUNT_OFFSET)).isEqualTo(1);
        assertThat(buffer.getInt(indexBase + BUCKET_ID_OFFSET)).isEqualTo(32);
        assertThat(buffer.getInt(indexBase + BUCKET_DEPTH_OFFSET)).isEqualTo(33);
        indexBase += BUCKET_DATA_OFFSET;

        // first block
        assertThat(buffer.getInt(indexBase)).isEqualTo(10 * 33);
        assertThat(buffer.getInt(indexBase + SIZE_OF_LONG)).isEqualTo(0xFF * 33);

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                              READ                                              //
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void shouldReadBucketArrayFromStream() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(3, 3);

        keyHandler.theKey = 10L;
        valueHandler.theValue = 0xFFL;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[16];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);

        assertThat((long) outputStream.size()).isEqualTo(bucketBufferArray.size());

        // when
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        final BucketBufferArray newBucketBufferArray = new BucketBufferArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
        newBucketBufferArray.readFromStream(inputStream, writeBuffer);

        // then
        assertThat(newBucketBufferArray.getCapacity()).isEqualTo((int) bucketBufferArray.getCapacity());
        assertThat(newBucketBufferArray.getBucketCount()).isEqualTo(1);
        assertThat(newBucketBufferArray.getBlockCount()).isEqualTo(bucketBufferArray.getBlockCount());

        assertThat(newBucketBufferArray.getBucketFillCount(newBucketAddress)).isEqualTo(1);
        assertThat(newBucketBufferArray.getBucketId(newBucketAddress)).isEqualTo(3);
        assertThat(newBucketBufferArray.getBucketDepth(newBucketAddress)).isEqualTo(3);

        newBucketBufferArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10L);

        newBucketBufferArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFFL);
        newBucketBufferArray.close();
    }

    @Test
    public void shouldReadWithSmallBuffer() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(3, 3);

        keyHandler.theKey = 10L;
        valueHandler.theValue = 0xFFL;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[1];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);

        assertThat((long) outputStream.size()).isEqualTo(bucketBufferArray.size());

        // when
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        final BucketBufferArray newBucketBufferArray = new BucketBufferArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
        newBucketBufferArray.readFromStream(inputStream, writeBuffer);

        // then
        assertThat(newBucketBufferArray.getCapacity()).isEqualTo((int) bucketBufferArray.getCapacity());
        assertThat(newBucketBufferArray.getBucketCount()).isEqualTo(1);
        assertThat(newBucketBufferArray.getBlockCount()).isEqualTo(bucketBufferArray.getBlockCount());

        assertThat(newBucketBufferArray.getBucketFillCount(newBucketAddress)).isEqualTo(1);
        assertThat(newBucketBufferArray.getBucketId(newBucketAddress)).isEqualTo(3);
        assertThat(newBucketBufferArray.getBucketDepth(newBucketAddress)).isEqualTo(3);

        newBucketBufferArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10L);

        newBucketBufferArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFFL);
        newBucketBufferArray.close();
    }

    @Test
    public void shouldReadWithLargeBuffer() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(3, 3);

        keyHandler.theKey = 10L;
        valueHandler.theValue = 0xFFL;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[4096];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);

        assertThat((long) outputStream.size()).isEqualTo(bucketBufferArray.size());

        // when
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        final BucketBufferArray newBucketBufferArray = new BucketBufferArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
        newBucketBufferArray.readFromStream(inputStream, writeBuffer);

        // then
        assertThat(newBucketBufferArray.getCapacity()).isEqualTo((int) bucketBufferArray.getCapacity());
        assertThat(newBucketBufferArray.getBucketCount()).isEqualTo(1);
        assertThat(newBucketBufferArray.getBlockCount()).isEqualTo(bucketBufferArray.getBlockCount());

        assertThat(newBucketBufferArray.getBucketFillCount(newBucketAddress)).isEqualTo(1);
        assertThat(newBucketBufferArray.getBucketId(newBucketAddress)).isEqualTo(3);
        assertThat(newBucketBufferArray.getBucketDepth(newBucketAddress)).isEqualTo(3);

        newBucketBufferArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10L);

        newBucketBufferArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFFL);
        newBucketBufferArray.close();
    }

    @Test
    public void shouldOverwriteWithReadBucketArrayFromStream() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(3, 3);

        keyHandler.theKey = 10L;
        valueHandler.theValue = 0xFFL;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        final int newBlockOffset = bucketBufferArray.getFirstBlockOffset();

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[16];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);

        // when
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        final BucketBufferArray newBucketBufferArray = new BucketBufferArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
        newBucketBufferArray.allocateNewBucket(4, 4);
        keyHandler.theKey = 14L;
        valueHandler.theValue = 0xAAL;
        newBucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        newBucketBufferArray.getFirstBlockOffset();
        newBucketBufferArray.readFromStream(inputStream, writeBuffer);

        // then
        assertThat(newBucketBufferArray.getCapacity()).isEqualTo((int) bucketBufferArray.getCapacity());
        assertThat(newBucketBufferArray.getBucketCount()).isEqualTo(1);
        assertThat(newBucketBufferArray.getBlockCount()).isEqualTo(bucketBufferArray.getBlockCount());

        assertThat(newBucketBufferArray.getBucketFillCount(newBucketAddress)).isEqualTo(2);
        assertThat(newBucketBufferArray.getBucketId(newBucketAddress)).isEqualTo(3);
        assertThat(newBucketBufferArray.getBucketDepth(newBucketAddress)).isEqualTo(3);

        newBucketBufferArray.readKey(keyHandler, newBucketAddress, newBlockOffset);
        assertThat(keyHandler.theKey).isEqualTo(10L);

        newBucketBufferArray.readValue(valueHandler, newBucketAddress, newBlockOffset);
        assertThat(valueHandler.theValue).isEqualTo(0xFFL);
        newBucketBufferArray.close();
    }

    @Test
    public void shouldReadLargeBucketArrayFromStream() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        for (int i = 0; i < ALLOCATION_FACTOR + 1; i++)
        {
            final long newBucketAddress = bucketBufferArray.allocateNewBucket(3, 3);
            keyHandler.theKey = 10L;
            valueHandler.theValue = 0xFFL;
            bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
        }

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[16];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);

        // when
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        final BucketBufferArray newBucketBufferArray = new BucketBufferArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
        newBucketBufferArray.readFromStream(inputStream, writeBuffer);

        // then
        assertThat(newBucketBufferArray.getCountOfUsedBytes()).isEqualTo((int) bucketBufferArray.getCountOfUsedBytes());
        assertThat(newBucketBufferArray.getBucketCount()).isEqualTo(33);
        assertThat(newBucketBufferArray.getBlockCount()).isEqualTo(bucketBufferArray.getBlockCount());
        newBucketBufferArray.close();
    }


    @Test
    public void shouldReadLargeDataSetIntoNewBucketArray() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final int blockCountPerBucket = 16;
        final int blockSize = 16;
        final int bucketBuffers = (int) Math.ceil(Math.ceil((double) DATA_COUNT / (double) ALLOCATION_FACTOR) / (double) blockCountPerBucket);
        final BucketBufferArray bucketBufferArray = createFilledBucketBufferArray(keyHandler, valueHandler, blockCountPerBucket, bucketBuffers);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[4096];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);

        final byte[] writtenBytes = outputStream.toByteArray();
        assertThat(writtenBytes.length).isEqualTo((int) bucketBufferArray.size());

        // when
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(writtenBytes);
        final BucketBufferArray newBucketBufferArray = new BucketBufferArray(16, 8, 8);
        final byte[] readBuffer = new byte[4096];
        newBucketBufferArray.readFromStream(inputStream, readBuffer);

        // then we expect
        // bucket buffer main header
        assertThat(newBucketBufferArray.getBucketBufferCount()).isEqualTo(bucketBuffers);
        assertThat(newBucketBufferArray.getBucketCount()).isEqualTo(bucketBuffers * ALLOCATION_FACTOR);
        assertThat(newBucketBufferArray.getBlockCount()).isEqualTo(bucketBuffers * ALLOCATION_FACTOR * blockCountPerBucket);

        // data
        for (int bucketBufferIndex = 0; bucketBufferIndex < bucketBuffers; bucketBufferIndex++)
        {
            assertThat(newBucketBufferArray.getBucketCount(bucketBufferIndex)).isEqualTo(ALLOCATION_FACTOR);

            for (int bucketIndex = 0; bucketIndex < ALLOCATION_FACTOR; bucketIndex++)
            {
                final long bucketAddress = getBucketAddress(bucketBufferIndex,
                                                            BUCKET_BUFFER_HEADER_LENGTH + bucketIndex * newBucketBufferArray.getMaxBucketLength());

                assertThat(newBucketBufferArray.getBucketFillCount(bucketAddress)).isEqualTo(blockCountPerBucket);
                assertThat(newBucketBufferArray.getBucketId(bucketAddress)).isEqualTo(bucketBufferIndex + bucketIndex);
                assertThat(newBucketBufferArray.getBucketDepth(bucketAddress)).isEqualTo(bucketBufferIndex + bucketIndex + 1);
                assertThat(newBucketBufferArray.getBucketOverflowPointer(bucketAddress)).isEqualTo(0);
                assertThat(newBucketBufferArray.getBucketLength(bucketAddress)).isEqualTo(BUCKET_HEADER_LENGTH + blockCountPerBucket * blockSize);

                keyHandler.theKey = 10L;
                valueHandler.theValue = 0xFFL;
                for (int blockIndex = 0; blockIndex < blockCountPerBucket; blockIndex++)
                {
                    final int blockOffset = BUCKET_HEADER_LENGTH + blockIndex * 2 * SIZE_OF_LONG;
                    assertThat(newBucketBufferArray.getBlockLength()).isEqualTo(blockSize);

                    newBucketBufferArray.readKey(keyHandler, bucketAddress, blockOffset);
                    assertThat(keyHandler.theKey).isEqualTo(10L + bucketBufferIndex * bucketIndex * blockIndex);

                    newBucketBufferArray.readValue(valueHandler, bucketAddress, blockOffset);
                    assertThat(valueHandler.theValue).isEqualTo(0xFFL + bucketBufferIndex * bucketIndex * blockIndex);
                }
            }
        }

        // finally
        bucketBufferArray.close();
        newBucketBufferArray.close();
    }

    @Test
    public void shouldReadLargeDataSetIntoSameBucketArray() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final int blockCountPerBucket = 16;
        final int blockSize = 16;
        final int bucketBuffers = (int) Math.ceil(Math.ceil((double) DATA_COUNT / (double) ALLOCATION_FACTOR) / (double) blockCountPerBucket);
        final BucketBufferArray bucketBufferArray = createFilledBucketBufferArray(keyHandler, valueHandler, blockCountPerBucket, bucketBuffers);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[4096];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);

        final byte[] writtenBytes = outputStream.toByteArray();
        assertThat(writtenBytes.length).isEqualTo((int) bucketBufferArray.size());

        // when
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(writtenBytes);
        final byte[] readBuffer = new byte[4096];
        bucketBufferArray.readFromStream(inputStream, readBuffer);

        // then we expect
        // bucket buffer main header
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(bucketBuffers);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(bucketBuffers * ALLOCATION_FACTOR);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(bucketBuffers * ALLOCATION_FACTOR * blockCountPerBucket);

        // data
        for (int bucketBufferIndex = 0; bucketBufferIndex < bucketBuffers; bucketBufferIndex++)
        {
            assertThat(bucketBufferArray.getBucketCount(bucketBufferIndex)).isEqualTo(ALLOCATION_FACTOR);

            for (int bucketIndex = 0; bucketIndex < ALLOCATION_FACTOR; bucketIndex++)
            {
                final long bucketAddress = getBucketAddress(bucketBufferIndex,
                                                            BUCKET_BUFFER_HEADER_LENGTH + bucketIndex * bucketBufferArray.getMaxBucketLength());

                assertThat(bucketBufferArray.getBucketFillCount(bucketAddress)).isEqualTo(blockCountPerBucket);
                assertThat(bucketBufferArray.getBucketId(bucketAddress)).isEqualTo(bucketBufferIndex + bucketIndex);
                assertThat(bucketBufferArray.getBucketDepth(bucketAddress)).isEqualTo(bucketBufferIndex + bucketIndex + 1);
                assertThat(bucketBufferArray.getBucketOverflowPointer(bucketAddress)).isEqualTo(0);
                assertThat(bucketBufferArray.getBucketLength(bucketAddress)).isEqualTo(BUCKET_HEADER_LENGTH + blockCountPerBucket * blockSize);

                keyHandler.theKey = 10L;
                valueHandler.theValue = 0xFFL;
                for (int blockIndex = 0; blockIndex < blockCountPerBucket; blockIndex++)
                {
                    final int blockOffset = BUCKET_HEADER_LENGTH + blockIndex * 2 * SIZE_OF_LONG;
                    assertThat(bucketBufferArray.getBlockLength()).isEqualTo(blockSize);

                    bucketBufferArray.readKey(keyHandler, bucketAddress, blockOffset);
                    assertThat(keyHandler.theKey).isEqualTo(10L + bucketBufferIndex * bucketIndex * blockIndex);

                    bucketBufferArray.readValue(valueHandler, bucketAddress, blockOffset);
                    assertThat(valueHandler.theValue).isEqualTo(0xFFL + bucketBufferIndex * bucketIndex * blockIndex);
                }
            }
        }

        // finally
        bucketBufferArray.close();
    }

    @Test
    public void shouldReadLargeDataSetIntoSameBucketArrayWithClear() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final int blockCountPerBucket = 16;
        final int blockSize = 16;
        final int bucketBuffers = (int) Math.ceil(Math.ceil((double) DATA_COUNT / (double) ALLOCATION_FACTOR) / (double) blockCountPerBucket);
        final BucketBufferArray bucketBufferArray = createFilledBucketBufferArray(keyHandler, valueHandler, blockCountPerBucket, bucketBuffers);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[4096];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);

        final byte[] writtenBytes = outputStream.toByteArray();
        assertThat(writtenBytes.length).isEqualTo((int) bucketBufferArray.size());

        // clear
        bucketBufferArray.clear();
        assertThat(bucketBufferArray.getCountOfUsedBytes()).isEqualTo(BUCKET_BUFFER_HEADER_LENGTH);

        // when
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(writtenBytes);
        final byte[] readBuffer = new byte[4096];
        bucketBufferArray.readFromStream(inputStream, readBuffer);

        // then we expect
        // bucket buffer main header
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(bucketBuffers);
        assertThat(bucketBufferArray.getBucketCount()).isEqualTo(bucketBuffers * ALLOCATION_FACTOR);
        assertThat(bucketBufferArray.getBlockCount()).isEqualTo(bucketBuffers * ALLOCATION_FACTOR * blockCountPerBucket);

        // data
        for (int bucketBufferIndex = 0; bucketBufferIndex < bucketBuffers; bucketBufferIndex++)
        {
            assertThat(bucketBufferArray.getBucketCount(bucketBufferIndex)).isEqualTo(ALLOCATION_FACTOR);

            for (int bucketIndex = 0; bucketIndex < ALLOCATION_FACTOR; bucketIndex++)
            {
                final long bucketAddress = getBucketAddress(bucketBufferIndex,
                                                            BUCKET_BUFFER_HEADER_LENGTH + bucketIndex * bucketBufferArray.getMaxBucketLength());

                assertThat(bucketBufferArray.getBucketFillCount(bucketAddress)).isEqualTo(blockCountPerBucket);
                assertThat(bucketBufferArray.getBucketId(bucketAddress)).isEqualTo(bucketBufferIndex + bucketIndex);
                assertThat(bucketBufferArray.getBucketDepth(bucketAddress)).isEqualTo(bucketBufferIndex + bucketIndex + 1);
                assertThat(bucketBufferArray.getBucketOverflowPointer(bucketAddress)).isEqualTo(0);
                assertThat(bucketBufferArray.getBucketLength(bucketAddress)).isEqualTo(BUCKET_HEADER_LENGTH + blockCountPerBucket * blockSize);

                keyHandler.theKey = 10L;
                valueHandler.theValue = 0xFFL;
                for (int blockIndex = 0; blockIndex < blockCountPerBucket; blockIndex++)
                {
                    final int blockOffset = BUCKET_HEADER_LENGTH + blockIndex * 2 * SIZE_OF_LONG;
                    assertThat(bucketBufferArray.getBlockLength()).isEqualTo(blockSize);

                    bucketBufferArray.readKey(keyHandler, bucketAddress, blockOffset);
                    assertThat(keyHandler.theKey).isEqualTo(10L + bucketBufferIndex * bucketIndex * blockIndex);

                    bucketBufferArray.readValue(valueHandler, bucketAddress, blockOffset);
                    assertThat(valueHandler.theValue).isEqualTo(0xFFL + bucketBufferIndex * bucketIndex * blockIndex);
                }
            }
        }

        // finally
        bucketBufferArray.close();
    }

    @Test
    public void shouldReadLargeDataSetIntoNewBucketArrayWithRepeatlyFailingInputStream() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final int blockCountPerBucket = 16;
        final int blockSize = 16;
        final int bucketBuffers = (int) Math.ceil(Math.ceil((double) DATA_COUNT / (double) ALLOCATION_FACTOR) / (double) blockCountPerBucket);
        final BucketBufferArray bucketBufferArray = createFilledBucketBufferArray(keyHandler, valueHandler, blockCountPerBucket, bucketBuffers);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[4096];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);

        final byte[] writtenBytes = outputStream.toByteArray();
        assertThat(writtenBytes.length).isEqualTo((int) bucketBufferArray.size());

        // when
        final RepeatedlyFailingInputStream inputStream = new RepeatedlyFailingInputStream(new ByteArrayInputStream(writtenBytes));
        final BucketBufferArray newBucketBufferArray = new BucketBufferArray(16, 8, 8);
        final byte[] readBuffer = new byte[4096];
        newBucketBufferArray.readFromStream(inputStream, readBuffer);

        // then we expect
        // bucket buffer main header
        assertThat(newBucketBufferArray.getBucketBufferCount()).isEqualTo(bucketBuffers);
        assertThat(newBucketBufferArray.getBucketCount()).isEqualTo(bucketBuffers * ALLOCATION_FACTOR);
        assertThat(newBucketBufferArray.getBlockCount()).isEqualTo(bucketBuffers * ALLOCATION_FACTOR * blockCountPerBucket);

        // data
        for (int bucketBufferIndex = 0; bucketBufferIndex < bucketBuffers; bucketBufferIndex++)
        {
            assertThat(newBucketBufferArray.getBucketCount(bucketBufferIndex)).isEqualTo(ALLOCATION_FACTOR);

            for (int bucketIndex = 0; bucketIndex < ALLOCATION_FACTOR; bucketIndex++)
            {
                final long bucketAddress = getBucketAddress(bucketBufferIndex,
                                                            BUCKET_BUFFER_HEADER_LENGTH + bucketIndex * newBucketBufferArray.getMaxBucketLength());

                assertThat(newBucketBufferArray.getBucketFillCount(bucketAddress)).isEqualTo(blockCountPerBucket);
                assertThat(newBucketBufferArray.getBucketId(bucketAddress)).isEqualTo(bucketBufferIndex + bucketIndex);
                assertThat(newBucketBufferArray.getBucketDepth(bucketAddress)).isEqualTo(bucketBufferIndex + bucketIndex + 1);
                assertThat(newBucketBufferArray.getBucketOverflowPointer(bucketAddress)).isEqualTo(0);
                assertThat(newBucketBufferArray.getBucketLength(bucketAddress)).isEqualTo(BUCKET_HEADER_LENGTH + blockCountPerBucket * blockSize);

                keyHandler.theKey = 10;
                valueHandler.theValue = 0xFF;
                for (int blockIndex = 0; blockIndex < blockCountPerBucket; blockIndex++)
                {
                    final int blockOffset = BUCKET_HEADER_LENGTH + blockIndex * 2 * SIZE_OF_LONG;
                    assertThat(newBucketBufferArray.getBlockLength()).isEqualTo(blockSize);

                    newBucketBufferArray.readKey(keyHandler, bucketAddress, blockOffset);
                    assertThat(keyHandler.theKey).isEqualTo(10 + bucketBufferIndex * bucketIndex * blockIndex);

                    newBucketBufferArray.readValue(valueHandler, bucketAddress, blockOffset);
                    assertThat(valueHandler.theValue).isEqualTo(0xFF + bucketBufferIndex * bucketIndex * blockIndex);
                }
            }
        }

        // finally
        bucketBufferArray.close();
        newBucketBufferArray.close();
    }


    @Test
    public void shouldThrowExceptionOnReadIOException() throws IOException
    {
        // given
        final AlwaysFailingInputStream inputStream = new AlwaysFailingInputStream();
        final BucketBufferArray newBucketBufferArray = new BucketBufferArray(16, 8, 8);
        final byte[] readBuffer = new byte[4096];

        try
        {
            // when
            newBucketBufferArray.readFromStream(inputStream, readBuffer);
            fail();
        }
        catch (IOException ioe)
        {
            assertThat(ioe).hasMessage("Failed to read bucket buffer array, managed to read 0 bytes.");
        }

        // then we expect
        // bucket buffer main header
        assertThat(newBucketBufferArray.getBucketBufferCount()).isEqualTo(1);
        assertThat(newBucketBufferArray.getBucketCount()).isEqualTo(0);
        assertThat(newBucketBufferArray.getBlockCount()).isEqualTo(0);

        // finally
        newBucketBufferArray.close();
    }

    @Test
    public void shouldReadWriteBucketArrayFromStream() throws IOException
    {
        // given
        final LongKeyHandler keyHandler = new LongKeyHandler();
        final LongValueHandler valueHandler = new LongValueHandler();
        final long newBucketAddress = bucketBufferArray.allocateNewBucket(3, 3);

        keyHandler.theKey = 10L;
        valueHandler.theValue = 0xFFL;
        bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] writeBuffer = new byte[16];
        bucketBufferArray.writeToStream(outputStream, writeBuffer);

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        final BucketBufferArray newBucketBufferArray = new BucketBufferArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
        newBucketBufferArray.readFromStream(inputStream, writeBuffer);

        // when again written
        outputStream = new ByteArrayOutputStream();
        newBucketBufferArray.writeToStream(outputStream, writeBuffer);

        assertThat((long) outputStream.size())
            .isEqualTo(newBucketBufferArray.size())
            .isEqualTo(bucketBufferArray.size());

        // then
        final byte writtenBytes[] = outputStream.toByteArray();
        final UnsafeBuffer buffer = new UnsafeBuffer(writtenBytes);

        // bucket buffer main header
        assertThat(buffer.getInt(MAIN_BUFFER_COUNT_OFFSET)).isEqualTo(1);
        assertThat(buffer.getInt(MAIN_BUCKET_COUNT_OFFSET)).isEqualTo(1);
        assertThat(buffer.getLong(MAIN_BLOCK_COUNT_OFFSET)).isEqualTo(1L);

        // first bucket buffer
        assertThat(buffer.getInt(MAIN_BUCKET_BUFFER_HEADER_LEN + BUCKET_BUFFER_BUCKET_COUNT_OFFSET)).isEqualTo(1);

        // first bucket
        final int bucketOffset = MAIN_BUCKET_BUFFER_HEADER_LEN + BUCKET_BUFFER_HEADER_LENGTH;
        assertThat(buffer.getInt(bucketOffset + BUCKET_FILL_COUNT_OFFSET)).isEqualTo(1);
        assertThat(buffer.getInt(bucketOffset + BUCKET_ID_OFFSET)).isEqualTo(3);
        assertThat(buffer.getInt(bucketOffset + BUCKET_DEPTH_OFFSET)).isEqualTo(3);
        assertThat(buffer.getLong(bucketOffset + BUCKET_OVERFLOW_POINTER_OFFSET)).isEqualTo(0L);

        // first block
        final int blockOffset = MAIN_BUCKET_BUFFER_HEADER_LEN + BUCKET_BUFFER_HEADER_LENGTH + BUCKET_DATA_OFFSET;
        assertThat(buffer.getLong(blockOffset + BLOCK_KEY_OFFSET)).isEqualTo(10L);
        assertThat(buffer.getLong((int) getBlockValueOffset(blockOffset, SIZE_OF_LONG))).isEqualTo(0xFFL);
    }

    private BucketBufferArray createFilledBucketBufferArray(LongKeyHandler keyHandler,
                                                            LongValueHandler valueHandler,
                                                            int blockCountPerBucket,
                                                            int bucketBuffers)
    {
        final BucketBufferArray bucketBufferArray = new BucketBufferArray(blockCountPerBucket, 8, 8);

        for (int i = 0; i < bucketBuffers; i++)
        {
            for (int bufferBucketIdx = 0; bufferBucketIdx < ALLOCATION_FACTOR; bufferBucketIdx++)
            {
                final long newBucketAddress = bucketBufferArray.allocateNewBucket(i + bufferBucketIdx, i + bufferBucketIdx + 1);
                for (int bucketBlockIdx = 0; bucketBlockIdx < blockCountPerBucket; bucketBlockIdx++)
                {
                    keyHandler.theKey = 10L + i * bufferBucketIdx * bucketBlockIdx;
                    valueHandler.theValue = 0xFFL + i * bufferBucketIdx * bucketBlockIdx;
                    bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
                }
            }
        }
        assertThat(bucketBufferArray.getBucketBufferCount()).isEqualTo(bucketBuffers);
        return bucketBufferArray;
    }
}

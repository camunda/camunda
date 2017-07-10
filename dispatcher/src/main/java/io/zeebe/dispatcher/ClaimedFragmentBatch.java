/**
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
package io.zeebe.dispatcher;

import static org.agrona.UnsafeAccess.UNSAFE;
import static io.zeebe.dispatcher.impl.PositionUtil.position;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.*;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;

/**
 * A claimed batch of fragments in the buffer. Use
 * {@link #nextFragment(int, int)} to add a new fragment to the batch. Write the
 * fragment message using {@link #getBuffer()} and {@link #getFragmentOffset()}
 * to get the buffer offset of this fragment. Complete the whole batch operation
 * by calling either {@link #commit()} or {@link #abort()}.
 *
 * <p>
 * <b>The claimed batch is reusable but not thread-safe.</b>
 */
public class ClaimedFragmentBatch
{
    private static final String ERROR_MESSAGE = "The given fragment length is greater than the remaining capacity. offset: %d, length: %d, capacity: %d";

    private static final int FIRST_FRAGMENT_OFFSET = 0;

    private final UnsafeBuffer buffer;

    private int partitionId;
    private int partitionOffset;

    private int currentOffset;
    private int nextOffset;

    public ClaimedFragmentBatch()
    {
        buffer = new UnsafeBuffer(0, 0);
    }

    public void wrap(UnsafeBuffer underlyingbuffer, int partitionId, int fragmentOffset, int fragmentLength)
    {
        buffer.wrap(underlyingbuffer, fragmentOffset, fragmentLength);

        this.partitionId = partitionId;
        this.partitionOffset = fragmentOffset;

        currentOffset = 0;
        nextOffset = 0;
    }

    /**
     * @return the claimed batch buffer to write in.
     */
    public MutableDirectBuffer getBuffer()
    {
        return buffer;
    }

    /**
     * @return the buffer offset of the last batch fragment
     */
    public int getFragmentOffset()
    {
        return currentOffset + HEADER_LENGTH;
    }

    /**
     * Add a new fragment to the batch.
     *
     * @param length
     *            the length of the fragment
     * @param streamId
     *            the stream id of the fragment
     * @return the position of the fragment
     *
     * @throws IllegalArgumentException
     *             if the given length is greater than the remaining capacity.
     *             In this case, you should try with smaller length, or abort
     *             the whole batch.
     */
    @SuppressWarnings("restriction")
    public long nextFragment(int length, int streamId)
    {
        currentOffset = nextOffset;

        nextOffset += DataFrameDescriptor.alignedLength(length);

        // ensure that there is enough capacity for padding message, or less than frame alignment which omits the padding message
        final int remainingCapacity = buffer.capacity() - nextOffset;
        if (remainingCapacity < 0 || (FRAME_ALIGNMENT <= remainingCapacity && remainingCapacity < HEADER_LENGTH))
        {
            throw new IllegalArgumentException(String.format(ERROR_MESSAGE, currentOffset, length, buffer.capacity()));
        }

        // set negative length => uncommitted fragment
        buffer.putIntOrdered(lengthOffset(currentOffset), -length);
        UNSAFE.storeFence();
        buffer.putShort(typeOffset(currentOffset), TYPE_MESSAGE);
        buffer.putInt(streamIdOffset(currentOffset), streamId);

        return position(partitionId, partitionOffset + nextOffset);
    }

    /**
     * Commit all fragments of the batch so that it can be read by
     * subscriptions.
     */
    public void commit()
    {
        final int firstFragmentLength = -buffer.getInt(lengthOffset(FIRST_FRAGMENT_OFFSET));

        // do not set batch flags if only one fragment in the batch
        if (currentOffset > 0)
        {
            // set batch begin flag
            final byte firstFragmentFlags = buffer.getByte(flagsOffset(FIRST_FRAGMENT_OFFSET));
            buffer.putByte(flagsOffset(FIRST_FRAGMENT_OFFSET), enableFlagBatchBegin(firstFragmentFlags));

            // set positive length => commit fragment
            int fragmentOffset = DataFrameDescriptor.alignedLength(firstFragmentLength);
            while (fragmentOffset < nextOffset)
            {
                final int fragmentLength = -buffer.getInt(lengthOffset(fragmentOffset));
                buffer.putInt(lengthOffset(fragmentOffset), fragmentLength);

                fragmentOffset += DataFrameDescriptor.alignedLength(fragmentLength);
            }

            // set batch end flag
            final byte lastFragmentFlags = buffer.getByte(flagsOffset(currentOffset));
            buffer.putByte(flagsOffset(currentOffset), enableFlagBatchEnd(lastFragmentFlags));
        }

        fillRemainingBatchSize();

        // commit the first fragment at the end so that the batch can be read at
        // once
        buffer.putIntOrdered(lengthOffset(FIRST_FRAGMENT_OFFSET), firstFragmentLength);

        reset();
    }

    /**
     * Commit all fragments of the batch and mark them as failed. They will be
     * ignored by subscriptions.
     */
    public void abort()
    {
        // discard all fragments by set the type to padding
        int fragmentOffset = 0;
        while (fragmentOffset < nextOffset)
        {
            final int fragmentLength = -buffer.getInt(lengthOffset(fragmentOffset));
            buffer.putInt(typeOffset(fragmentOffset), TYPE_PADDING);
            buffer.putIntOrdered(lengthOffset(fragmentOffset), fragmentLength);

            fragmentOffset += DataFrameDescriptor.alignedLength(fragmentLength);
        }

        fillRemainingBatchSize();

        reset();
    }

    private void fillRemainingBatchSize()
    {
        // since the claimed batch size can be longer than the written fragment
        // size, we need to fill the rest with a padding fragment
        final int remainingLength = buffer.capacity() - nextOffset - HEADER_LENGTH;
        if (remainingLength >= 0)
        {
            buffer.putInt(lengthOffset(nextOffset), remainingLength);
            buffer.putShort(typeOffset(nextOffset), TYPE_PADDING);
        }
    }

    private void reset()
    {
        buffer.wrap(0, 0);
    }

}

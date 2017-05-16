package org.camunda.tngp.dispatcher.impl.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.dispatcher.impl.PositionUtil.position;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.TYPE_MESSAGE;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.TYPE_PADDING;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.enableFlagBatchBegin;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.enableFlagBatchEnd;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.flagsOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.streamIdOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.typeOffset;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.dispatcher.ClaimedFragmentBatch;
import org.junit.Before;
import org.junit.Test;

public class ClaimedFragmentBatchTest
{
    private static final int PARTITION_ID = 1;
    private static final int PARTITION_OFFSET = 16;
    private static final int FRAGMENT_LENGTH = 1024;

    private static final byte[] MESSAGE = "message".getBytes();
    private static final int MESSAGE_LENGTH = MESSAGE.length;

    private UnsafeBuffer underlyingBuffer;
    private ClaimedFragmentBatch claimedBatch;

    @Before
    public void init()
    {
        underlyingBuffer = new UnsafeBuffer(new byte[PARTITION_OFFSET + FRAGMENT_LENGTH]);
        claimedBatch = new ClaimedFragmentBatch();
    }

    @Test
    public void shouldAddFragment()
    {
        // given
        claimedBatch.wrap(underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH);

        // when
        final long position = claimedBatch.nextFragment(MESSAGE_LENGTH, 1);

        final int fragmentOffset = claimedBatch.getFragmentOffset();
        claimedBatch.getBuffer().putBytes(fragmentOffset, MESSAGE);

        // then
        assertThat(position).isEqualTo(position(PARTITION_ID, PARTITION_OFFSET + alignedLength(MESSAGE_LENGTH)));
        assertThat(fragmentOffset).isEqualTo(HEADER_LENGTH);

        assertThat(underlyingBuffer.getInt(lengthOffset(PARTITION_OFFSET))).isEqualTo(-MESSAGE_LENGTH);
        assertThat(underlyingBuffer.getShort(typeOffset(PARTITION_OFFSET))).isEqualTo(TYPE_MESSAGE);
        assertThat(underlyingBuffer.getInt(streamIdOffset(PARTITION_OFFSET))).isEqualTo(1);

        final byte[] buffer = new byte[MESSAGE_LENGTH];
        underlyingBuffer.getBytes(messageOffset(PARTITION_OFFSET), buffer);
        assertThat(buffer).isEqualTo(MESSAGE);
    }

    @Test
    public void shouldAddMultipleFragments()
    {
        // given
        claimedBatch.wrap(underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH);

        claimedBatch.nextFragment(MESSAGE_LENGTH, 1);

        // when
        final long position = claimedBatch.nextFragment(MESSAGE_LENGTH, 2);
        final int fragmentOffset = claimedBatch.getFragmentOffset();

        // then
        assertThat(position).isEqualTo(position(PARTITION_ID, PARTITION_OFFSET + 2 * alignedLength(MESSAGE_LENGTH)));
        assertThat(fragmentOffset).isEqualTo(HEADER_LENGTH + alignedLength(MESSAGE_LENGTH));

        final int bufferOffset = PARTITION_OFFSET + alignedLength(MESSAGE_LENGTH);
        assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset))).isEqualTo(-MESSAGE_LENGTH);
        assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_MESSAGE);
        assertThat(underlyingBuffer.getInt(streamIdOffset(bufferOffset))).isEqualTo(2);
    }

    @Test
    public void shouldCommitBatch()
    {
        // given
        claimedBatch.wrap(underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH);

        claimedBatch.nextFragment(MESSAGE_LENGTH, 1);
        claimedBatch.nextFragment(MESSAGE_LENGTH, 2);

        // when
        claimedBatch.commit();

        // then
        int bufferOffset = PARTITION_OFFSET;
        assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset))).isEqualTo(MESSAGE_LENGTH);
        assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_MESSAGE);
        assertThat(underlyingBuffer.getByte(flagsOffset(bufferOffset))).isEqualTo(enableFlagBatchBegin((byte) 0));

        bufferOffset += alignedLength(MESSAGE_LENGTH);
        assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset))).isEqualTo(MESSAGE_LENGTH);
        assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_MESSAGE);
        assertThat(underlyingBuffer.getByte(flagsOffset(bufferOffset))).isEqualTo(enableFlagBatchEnd((byte) 0));
    }

    @Test
    public void shouldAbortBatch()
    {
        // given
        claimedBatch.wrap(underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH);

        claimedBatch.nextFragment(MESSAGE_LENGTH, 1);
        claimedBatch.nextFragment(MESSAGE_LENGTH, 2);

        // when
        claimedBatch.abort();

        // then
        int bufferOffset = PARTITION_OFFSET;
        assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset))).isEqualTo(MESSAGE_LENGTH);
        assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_PADDING);
        assertThat(underlyingBuffer.getByte(flagsOffset(bufferOffset))).isEqualTo((byte) 0);

        bufferOffset += alignedLength(MESSAGE_LENGTH);
        assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset))).isEqualTo(MESSAGE_LENGTH);
        assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_PADDING);
        assertThat(underlyingBuffer.getByte(flagsOffset(bufferOffset))).isEqualTo((byte) 0);
    }

    @Test
    public void shouldFillRemainingBatchLengthOnCommit()
    {
        // given
        claimedBatch.wrap(underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH);

        claimedBatch.nextFragment(MESSAGE_LENGTH, 1);
        claimedBatch.nextFragment(MESSAGE_LENGTH, 2);

        // when
        claimedBatch.commit();

        // then
        final int bufferOffset = PARTITION_OFFSET + 2 * alignedLength(MESSAGE_LENGTH);
        assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset))).isEqualTo(FRAGMENT_LENGTH - 2 * alignedLength(MESSAGE_LENGTH) - HEADER_LENGTH);
        assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_PADDING);
    }

    @Test
    public void shouldFillRemainingBatchLengthOnAbort()
    {
        // given
        claimedBatch.wrap(underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH);

        claimedBatch.nextFragment(MESSAGE_LENGTH, 1);
        claimedBatch.nextFragment(MESSAGE_LENGTH, 2);

        // when
        claimedBatch.abort();

        // then
        final int bufferOffset = PARTITION_OFFSET + 2 * alignedLength(MESSAGE_LENGTH);
        assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset))).isEqualTo(FRAGMENT_LENGTH - 2 * alignedLength(MESSAGE_LENGTH) - HEADER_LENGTH);
        assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_PADDING);
    }

    @Test
    public void shouldCommitSingleFragmentBatch()
    {
        // given
        claimedBatch.wrap(underlyingBuffer, PARTITION_ID, PARTITION_OFFSET, FRAGMENT_LENGTH);

        claimedBatch.nextFragment(MESSAGE_LENGTH, 1);

        // when
        claimedBatch.commit();

        // then
        int bufferOffset = PARTITION_OFFSET;
        assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset))).isEqualTo(MESSAGE_LENGTH);
        assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_MESSAGE);
        assertThat(underlyingBuffer.getByte(flagsOffset(bufferOffset))).isEqualTo((byte) 0);

        bufferOffset += alignedLength(MESSAGE_LENGTH);
        assertThat(underlyingBuffer.getInt(lengthOffset(bufferOffset))).isEqualTo(FRAGMENT_LENGTH - alignedLength(MESSAGE_LENGTH) - HEADER_LENGTH);
        assertThat(underlyingBuffer.getShort(typeOffset(bufferOffset))).isEqualTo(TYPE_PADDING);
        assertThat(underlyingBuffer.getByte(flagsOffset(bufferOffset))).isEqualTo((byte) 0);
    }

}

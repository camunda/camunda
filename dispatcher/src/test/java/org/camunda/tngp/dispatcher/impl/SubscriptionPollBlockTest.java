package org.camunda.tngp.dispatcher.impl;

import static org.mockito.Mockito.*;
import static org.agrona.BitUtil.*;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.*;
import static org.camunda.tngp.dispatcher.impl.PositionUtil.*;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;

import org.camunda.tngp.dispatcher.BlockHandler;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.impl.allocation.AllocatedBuffer;
import org.camunda.tngp.dispatcher.impl.log.LogBufferPartition;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.Position;

public class SubscriptionPollBlockTest
{
    static final int A_PARTITION_LENGTH = 1024;
    static final int A_MSG_PAYLOAD_LENGTH = 10;
    static final int A_FRAGMENT_LENGTH = align(A_MSG_PAYLOAD_LENGTH + HEADER_LENGTH, FRAME_ALIGNMENT);
    static final int A_PARTITION_ID = 10;
    static final int A_STREAM_ID = 20;
    static final int ANOTHER_STREAM_ID = 25;
    static final int A_PARTITION_DATA_SECTION_OFFSET = A_PARTITION_LENGTH;

    UnsafeBuffer metadataBufferMock;
    UnsafeBuffer dataBufferMock;
    LogBufferPartition logBufferPartition;
    Position mockSubscriberPosition;
    BlockHandler mockBlockHandler;
    AllocatedBuffer allocatedBufferMock;
    ByteBuffer rawBufferMock;

    Subscription subscription;

    @Before
    public void setup()
    {
        dataBufferMock = mock(UnsafeBuffer.class);
        metadataBufferMock = mock(UnsafeBuffer.class);
        rawBufferMock = mock(ByteBuffer.class);
        allocatedBufferMock = mock(AllocatedBuffer.class);

        when(dataBufferMock.capacity()).thenReturn(A_PARTITION_LENGTH);
        when(allocatedBufferMock.getRawBuffer()).thenReturn(rawBufferMock);
        logBufferPartition = new LogBufferPartition(dataBufferMock, metadataBufferMock, allocatedBufferMock, A_PARTITION_DATA_SECTION_OFFSET);

        mockSubscriberPosition = mock(Position.class);
        mockBlockHandler = mock(BlockHandler.class);
        subscription = new Subscription(mockSubscriberPosition, 0, mock(Dispatcher.class));
    }

    @Test
    public void shouldReadSingleFragment()
    {
        final int fragOffset = 0;

        final long subscriberPosition = position(A_PARTITION_ID, fragOffset);
        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(fragOffset))).thenReturn(A_STREAM_ID);

        // when
        final int fragmentsRead = subscription.pollBlock(logBufferPartition, mockBlockHandler, 1, A_PARTITION_ID, fragOffset, true);

        // then
        assertThat(fragmentsRead).isEqualTo(1);
        // the fragment handler was handed one fragment
        verify(mockBlockHandler).onBlockAvailable(eq(rawBufferMock), eq(A_PARTITION_DATA_SECTION_OFFSET + fragOffset), eq(A_FRAGMENT_LENGTH), eq(A_STREAM_ID), eq(subscriberPosition));
        verifyNoMoreInteractions(mockBlockHandler);
        // and the position was increased by the fragment length
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, nextFragmentOffset(fragOffset)));
    }

    @Test
    public void shouldReadMultipleFragmentsAsBlock()
    {
        final int firstFragOffset = 0;
        final int secondFragOffset = nextFragmentOffset(firstFragOffset);

        final long subscriberPosition = position(A_PARTITION_ID, firstFragOffset);

        when(dataBufferMock.getIntVolatile(firstFragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(firstFragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(firstFragOffset))).thenReturn(A_STREAM_ID);

        when(dataBufferMock.getIntVolatile(secondFragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(secondFragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(secondFragOffset))).thenReturn(A_STREAM_ID);

        // when
        final int fragmentsRead = subscription.pollBlock(logBufferPartition, mockBlockHandler, 2, A_PARTITION_ID, firstFragOffset, true);

        // then
        assertThat(fragmentsRead).isEqualTo(2);

        // the block handler was handed two fragments as blocks
        final InOrder inOrder = inOrder(mockBlockHandler);
        inOrder.verify(mockBlockHandler).onBlockAvailable(eq(rawBufferMock),
                eq(A_PARTITION_DATA_SECTION_OFFSET + firstFragOffset), eq(2 * A_FRAGMENT_LENGTH), eq(A_STREAM_ID),
                eq(subscriberPosition));
        inOrder.verifyNoMoreInteractions();

        // and the position was increased by the fragment length of the two fragments
        final int expectedPartitionOffset = nextFragmentOffset(secondFragOffset);
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, expectedPartitionOffset));
    }

    @Test
    public void shouldNotReadDifferentStreamsIfStreamAware()
    {
        final int firstFragOffset = 0;
        final int secondFragOffset = nextFragmentOffset(firstFragOffset);

        final long subscriberPosition = position(A_PARTITION_ID, firstFragOffset);

        when(dataBufferMock.getIntVolatile(firstFragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(firstFragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(firstFragOffset))).thenReturn(A_STREAM_ID);

        when(dataBufferMock.getIntVolatile(secondFragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(secondFragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(secondFragOffset))).thenReturn(ANOTHER_STREAM_ID); // different stream id than first msg

        // when
        final int fragmentsRead = subscription.pollBlock(logBufferPartition, mockBlockHandler, 2, A_PARTITION_ID, firstFragOffset, true);

        // then
        assertThat(fragmentsRead).isEqualTo(1);

        // the block handler was handed two fragments as blocks
        final InOrder inOrder = inOrder(mockBlockHandler);
        inOrder.verify(mockBlockHandler).onBlockAvailable(eq(rawBufferMock), eq(A_PARTITION_DATA_SECTION_OFFSET + firstFragOffset), eq(A_FRAGMENT_LENGTH), eq(A_STREAM_ID), eq(subscriberPosition));
        inOrder.verifyNoMoreInteractions();

        // and the position was increased by the fragment length of the two fragments
        final int expectedPartitionOffset = nextFragmentOffset(firstFragOffset);
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, expectedPartitionOffset));
    }

    @Test
    public void shouldReadDifferentStreamsIfNOTStreamAware()
    {
        final int firstFragOffset = 0;
        final int secondFragOffset = nextFragmentOffset(firstFragOffset);

        final long subscriberPosition = position(A_PARTITION_ID, firstFragOffset);

        when(dataBufferMock.getIntVolatile(firstFragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(firstFragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(firstFragOffset))).thenReturn(A_STREAM_ID);

        when(dataBufferMock.getIntVolatile(secondFragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(secondFragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(secondFragOffset))).thenReturn(ANOTHER_STREAM_ID); // different stream id than first msg

        // when
        final int fragmentsRead = subscription.pollBlock(logBufferPartition, mockBlockHandler, 2, A_PARTITION_ID, firstFragOffset, false);

        // then
        assertThat(fragmentsRead).isEqualTo(2);

        // the block handler was handed two fragments as blocks
        final InOrder inOrder = inOrder(mockBlockHandler);
        inOrder.verify(mockBlockHandler).onBlockAvailable(eq(rawBufferMock),
                eq(A_PARTITION_DATA_SECTION_OFFSET + firstFragOffset), eq(2 * A_FRAGMENT_LENGTH), eq(-1),
                eq(subscriberPosition));
        inOrder.verifyNoMoreInteractions();

        // and the position was increased by the fragment length of the two fragments
        final int expectedPartitionOffset = nextFragmentOffset(secondFragOffset);
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, expectedPartitionOffset));
    }

    @Test
    public void shouldRollOverPartitionOnPaddingIfEndOfPArtition()
    {
        final int fragOffset = A_PARTITION_LENGTH - A_FRAGMENT_LENGTH;

        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_PADDING);

        // when
        final int fragmentsRead = subscription.pollBlock(logBufferPartition, mockBlockHandler, 2, A_PARTITION_ID, fragOffset, true);

        // then
        assertThat(fragmentsRead).isEqualTo(0);
        // the block handler was not handed any fragments
        verifyNoMoreInteractions(mockBlockHandler);
        // the position was set to the beginning of the next partition
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID + 1, 0));
    }

    @Test
    public void shouldRollOverIfHitsPadding()
    {
        final int firstFragOffset = A_PARTITION_LENGTH - (2 * A_FRAGMENT_LENGTH);
        final int secondFragOffset = nextFragmentOffset(firstFragOffset);

        final long subscriberPosition = position(A_PARTITION_ID, firstFragOffset);

        when(dataBufferMock.getIntVolatile(firstFragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(firstFragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(firstFragOffset))).thenReturn(A_STREAM_ID);

        when(dataBufferMock.getIntVolatile(secondFragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(secondFragOffset))).thenReturn(TYPE_PADDING);

        // when
        final int fragmentsRead = subscription.pollBlock(logBufferPartition, mockBlockHandler, 2, A_PARTITION_ID, firstFragOffset, true);

        // then
        assertThat(fragmentsRead).isEqualTo(1);

        // the block handler was handed only one fragment in as block
        final InOrder inOrder = inOrder(mockBlockHandler);
        inOrder.verify(mockBlockHandler).onBlockAvailable(eq(rawBufferMock), eq(A_PARTITION_DATA_SECTION_OFFSET + firstFragOffset), eq(A_FRAGMENT_LENGTH), eq(A_STREAM_ID), eq(subscriberPosition));
        inOrder.verifyNoMoreInteractions();

        // and the position was rolled over to the next partition
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID + 1, 0));
    }

    @Test
    public void shouldNotRollOverPartitionOnPaddingIfNotEndOfPArtition()
    {
        final int fragOffset = 0;

        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_PADDING);

        // when
        final int fragmentsRead = subscription.pollBlock(logBufferPartition, mockBlockHandler, 2, A_PARTITION_ID, fragOffset, true);

        // then
        assertThat(fragmentsRead).isEqualTo(0);
        // the block handler was not handed any fragments
        verifyNoMoreInteractions(mockBlockHandler);
        // the position was set to the beginning of the next partition
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, nextFragmentOffset(fragOffset)));
    }

    @Test
    public void shouldNotRollOverIfHitsPaddingNotAtAndOfPartition()
    {
        final int firstFragOffset = 0;
        final int secondFragOffset = nextFragmentOffset(firstFragOffset);

        final long subscriberPosition = position(A_PARTITION_ID, firstFragOffset);

        when(dataBufferMock.getIntVolatile(firstFragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(firstFragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(firstFragOffset))).thenReturn(A_STREAM_ID);

        when(dataBufferMock.getIntVolatile(secondFragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(secondFragOffset))).thenReturn(TYPE_PADDING);

        // when
        final int fragmentsRead = subscription.pollBlock(logBufferPartition, mockBlockHandler, 2, A_PARTITION_ID, firstFragOffset, true);

        // then
        assertThat(fragmentsRead).isEqualTo(1);

        // the block handler was handed only one fragment in as block
        final InOrder inOrder = inOrder(mockBlockHandler);
        inOrder.verify(mockBlockHandler).onBlockAvailable(eq(rawBufferMock), eq(A_PARTITION_DATA_SECTION_OFFSET + firstFragOffset), eq(A_FRAGMENT_LENGTH), eq(A_STREAM_ID), eq(subscriberPosition));
        inOrder.verifyNoMoreInteractions();

        // and the position was rolled over to the next fragement after the padding
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, nextFragmentOffset(secondFragOffset)));
    }

    @Test
    public void shouldNotReadIncompleteMessage()
    {
        final int fragOffset = 0;

        when(mockSubscriberPosition.get()).thenReturn(position(A_PARTITION_ID, fragOffset));
        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(-A_MSG_PAYLOAD_LENGTH);

        // when
        final int fragmentsRead = subscription.pollBlock(logBufferPartition, mockBlockHandler, 1, A_PARTITION_ID, fragOffset, true);

        // then
        assertThat(fragmentsRead).isEqualTo(0);
        // the block handler was not handed any fragments
        verifyNoMoreInteractions(mockBlockHandler);
        // and the position was set but not increased
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, fragOffset));
    }

    private int nextFragmentOffset(final int currentOffset)
    {
        return currentOffset + A_FRAGMENT_LENGTH;
    }

}

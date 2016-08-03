package org.camunda.tngp.dispatcher.impl;

import static org.mockito.Mockito.*;
import static uk.co.real_logic.agrona.BitUtil.*;
import static org.assertj.core.api.Assertions.*;
import static org.camunda.tngp.dispatcher.impl.PositionUtil.*;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.impl.log.LogBufferPartition;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.agrona.concurrent.status.Position;

public class SubscriptionPollFragmentsTest
{
    static final int A_PARTITION_LENGTH = 1024;
    static final int A_MSG_PAYLOAD_LENGTH = 10;
    static final int A_FRAGMENT_LENGTH = align(A_MSG_PAYLOAD_LENGTH + HEADER_LENGTH, FRAME_ALIGNMENT);
    static final int A_PARTITION_ID = 10;
    static final int A_STREAM_ID = 20;

    UnsafeBuffer metadataBufferMock;
    UnsafeBuffer dataBufferMock;
    LogBufferPartition logBufferPartition;
    Position mockSubscriberPosition;
    FragmentHandler mockFragmentHandler;

    Subscription subscription;

    @Before
    public void setup()
    {
        dataBufferMock = mock(UnsafeBuffer.class);
        metadataBufferMock = mock(UnsafeBuffer.class);

        when(dataBufferMock.capacity()).thenReturn(A_PARTITION_LENGTH);
        logBufferPartition = new LogBufferPartition(dataBufferMock, metadataBufferMock, null, 0);

        mockSubscriberPosition = mock(Position.class);
        mockFragmentHandler = mock(FragmentHandler.class);
        subscription = new Subscription(mockSubscriberPosition, 0, mock(Dispatcher.class));
    }

    @Test
    public void shouldReadSingleFragment()
    {
        final int fragOffset = 0;

        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(fragOffset))).thenReturn(A_STREAM_ID);
        when(dataBufferMock.getByte(flagsOffset(fragOffset))).thenReturn((byte) 0);

        // when
        final int fragmentsRead = subscription.pollFragments(logBufferPartition, mockFragmentHandler, 1, A_PARTITION_ID, fragOffset, false);

        // then
        assertThat(fragmentsRead).isEqualTo(1);
        // the fragment handler was handed one fragment
        verify(mockFragmentHandler).onFragment(dataBufferMock, messageOffset(fragOffset), A_MSG_PAYLOAD_LENGTH, A_STREAM_ID, false);
        verifyNoMoreInteractions(mockFragmentHandler);
        // and the position was increased by the fragment length
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, nextFragmentOffset(fragOffset)));
    }

    @Test
    public void shouldReadSingleFailedFragment()
    {
        final int fragOffset = 0;

        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(fragOffset))).thenReturn(A_STREAM_ID);
        when(dataBufferMock.getByte(flagsOffset(fragOffset))).thenReturn(enableFlagFailed((byte) 0));

        // when
        final int fragmentsRead = subscription.pollFragments(logBufferPartition, mockFragmentHandler, 1, A_PARTITION_ID, fragOffset, false);

        // then
        assertThat(fragmentsRead).isEqualTo(1);
        // the fragment handler was handed one fragment
        verify(mockFragmentHandler).onFragment(dataBufferMock, messageOffset(fragOffset), A_MSG_PAYLOAD_LENGTH, A_STREAM_ID, true);
        verifyNoMoreInteractions(mockFragmentHandler);
        // and the position was increased by the fragment length
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, nextFragmentOffset(fragOffset)));
    }

    @Test
    public void shouldReadMultipleFragments()
    {
        final int firstFragOffset = 0;
        final int secondFragOffset = nextFragmentOffset(firstFragOffset);

        when(dataBufferMock.getIntVolatile(firstFragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(firstFragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(firstFragOffset))).thenReturn(A_STREAM_ID);
        when(dataBufferMock.getByte(flagsOffset(secondFragOffset))).thenReturn((byte) 0);

        when(dataBufferMock.getIntVolatile(secondFragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(secondFragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(secondFragOffset))).thenReturn(A_STREAM_ID);
        when(dataBufferMock.getByte(flagsOffset(secondFragOffset))).thenReturn(enableFlagFailed((byte) 0));

        // when
        final int fragmentsRead = subscription.pollFragments(logBufferPartition, mockFragmentHandler, 2, A_PARTITION_ID, firstFragOffset, false);

        // then
        assertThat(fragmentsRead).isEqualTo(2);

        // the fragment handler was handed two fragments
        final InOrder inOrder = inOrder(mockFragmentHandler);
        inOrder.verify(mockFragmentHandler).onFragment(dataBufferMock, messageOffset(firstFragOffset), A_MSG_PAYLOAD_LENGTH, A_STREAM_ID, false);
        inOrder.verify(mockFragmentHandler).onFragment(dataBufferMock, messageOffset(secondFragOffset), A_MSG_PAYLOAD_LENGTH, A_STREAM_ID, true);
        inOrder.verifyNoMoreInteractions();

        // and the position was increased by the fragment length
        final int expectedPartitionOffset = nextFragmentOffset(secondFragOffset);
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, expectedPartitionOffset));
    }

    @Test
    public void shouldRollOverOnPaddingAtEndOfPartition()
    {
        final int fragOffset = A_PARTITION_LENGTH - alignedLength(A_MSG_PAYLOAD_LENGTH);

        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_PADDING);

        // when
        final int fragmentsRead = subscription.pollFragments(logBufferPartition, mockFragmentHandler, 2, A_PARTITION_ID, fragOffset, false);

        // then
        assertThat(fragmentsRead).isEqualTo(0);
        // the fragment handler was not handed any fragments
        verifyNoMoreInteractions(mockFragmentHandler);
        // the position was set to the beginning of the next partition
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID + 1, 0));
    }

    @Test
    public void shouldNotRollOverOnPaddingIfNotEndOfPartition()
    {
        final int fragOffset = 0;

        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_PADDING);

        // when
        final int fragmentsRead = subscription.pollFragments(logBufferPartition, mockFragmentHandler, 2, A_PARTITION_ID, fragOffset, false);

        // then
        assertThat(fragmentsRead).isEqualTo(0);
        // the fragment handler was not handed any fragments
        verifyNoMoreInteractions(mockFragmentHandler);

        // and the position was increased by the fragment length
        final int expectedPartitionOffset = nextFragmentOffset(fragOffset);
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, expectedPartitionOffset));
    }

    @Test
    public void shouldNotReadIncompleteMessage()
    {
        final int fragOffset = 0;

        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(-A_MSG_PAYLOAD_LENGTH);

        // when
        final int fragmentsRead = subscription.pollFragments(logBufferPartition, mockFragmentHandler, 1, A_PARTITION_ID, fragOffset, false);

        // then
        assertThat(fragmentsRead).isEqualTo(0);
        // the fragment handler was not handed any fragments
        verifyNoMoreInteractions(mockFragmentHandler);
        // and the position was set but not increased
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, fragOffset));
    }

    @Test
    public void shouldNotUpdatePositionBasedOnHandlerPostponeResult()
    {
        final int fragOffset = 0;

        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(fragOffset))).thenReturn(A_STREAM_ID);
        when(dataBufferMock.getByte(flagsOffset(fragOffset))).thenReturn((byte) 0);
        when(mockFragmentHandler.onFragment(any(), anyInt(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(FragmentHandler.POSTPONE_FRAGMENT_RESULT);

        // when
        final int fragmentsRead = subscription.pollFragments(logBufferPartition, mockFragmentHandler, 1, A_PARTITION_ID, fragOffset, true);

        // then
        assertThat(fragmentsRead).isEqualTo(0);
        // and the position was not increased
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, fragOffset));
    }

    @Test
    public void shouldUpdatePositionBasedOnHandlerConsumeResult()
    {
        final int fragOffset = 0;

        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(fragOffset))).thenReturn(A_STREAM_ID);
        when(dataBufferMock.getByte(flagsOffset(fragOffset))).thenReturn((byte) 0);
        when(mockFragmentHandler.onFragment(any(), anyInt(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(FragmentHandler.CONSUME_FRAGMENT_RESULT);

        // when
        final int fragmentsRead = subscription.pollFragments(logBufferPartition, mockFragmentHandler, 1, A_PARTITION_ID, fragOffset, true);

        // then
        assertThat(fragmentsRead).isEqualTo(1);
        // and the position was increased
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, nextFragmentOffset(fragOffset)));
    }

    private int nextFragmentOffset(final int currentOffset)
    {
        return currentOffset + A_FRAGMENT_LENGTH;
    }

}

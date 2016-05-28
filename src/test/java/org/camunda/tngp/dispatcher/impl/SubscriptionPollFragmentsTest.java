package org.camunda.tngp.dispatcher.impl;

import static org.mockito.Mockito.*;
import static uk.co.real_logic.agrona.BitUtil.*;
import static org.assertj.core.api.Assertions.*;
import static org.camunda.tngp.dispatcher.impl.PositionUtil.*;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.impl.Subscription;
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
        int fragOffset = 0;

        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(fragOffset))).thenReturn(A_STREAM_ID);

        // when
        int fragmentsRead = subscription.pollFragments(logBufferPartition, mockFragmentHandler, 1, A_PARTITION_ID, fragOffset);

        // then
        assertThat(fragmentsRead).isEqualTo(1);
        // the fragment handler was handed one fragment
        verify(mockFragmentHandler).onFragment(eq(dataBufferMock), eq(messageOffset(fragOffset)), eq(A_MSG_PAYLOAD_LENGTH), eq(A_STREAM_ID));
        verifyNoMoreInteractions(mockFragmentHandler);
        // and the position was increased by the fragment length
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, nextFragmentOffset(fragOffset)));
    }

    @Test
    public void shouldReadMultipleFragments()
    {
        int firstFragOffset = 0;
        int secondFragOffset = nextFragmentOffset(firstFragOffset);

        when(dataBufferMock.getIntVolatile(firstFragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(firstFragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(firstFragOffset))).thenReturn(A_STREAM_ID);

        when(dataBufferMock.getIntVolatile(secondFragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(secondFragOffset))).thenReturn(TYPE_MESSAGE);
        when(dataBufferMock.getInt(streamIdOffset(secondFragOffset))).thenReturn(A_STREAM_ID);

        // when
        int fragmentsRead = subscription.pollFragments(logBufferPartition, mockFragmentHandler, 2, A_PARTITION_ID, firstFragOffset);

        // then
        assertThat(fragmentsRead).isEqualTo(2);

        // the fragment handler was handed two fragments
        InOrder inOrder = inOrder(mockFragmentHandler);
        inOrder.verify(mockFragmentHandler).onFragment(eq(dataBufferMock), eq(messageOffset(firstFragOffset)), eq(A_MSG_PAYLOAD_LENGTH), eq(A_STREAM_ID));
        inOrder.verify(mockFragmentHandler).onFragment(eq(dataBufferMock), eq(messageOffset(secondFragOffset)), eq(A_MSG_PAYLOAD_LENGTH), eq(A_STREAM_ID));
        inOrder.verifyNoMoreInteractions();

        // and the position was increased by the fragment length
        int expectedPartitionOffset = nextFragmentOffset(secondFragOffset);
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, expectedPartitionOffset));
    }

    @Test
    public void shouldRollOverOnPaddingAtEndOfPartition()
    {
        int fragOffset = A_PARTITION_LENGTH - alignedLength(A_MSG_PAYLOAD_LENGTH);

        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_PADDING);

        // when
        int fragmentsRead = subscription.pollFragments(logBufferPartition, mockFragmentHandler, 2, A_PARTITION_ID, fragOffset);

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
        int fragOffset = 0;

        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_PADDING);

        // when
        int fragmentsRead = subscription.pollFragments(logBufferPartition, mockFragmentHandler, 2, A_PARTITION_ID, fragOffset);

        // then
        assertThat(fragmentsRead).isEqualTo(0);
        // the fragment handler was not handed any fragments
        verifyNoMoreInteractions(mockFragmentHandler);

        // and the position was increased by the fragment length
        int expectedPartitionOffset = nextFragmentOffset(fragOffset);
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, expectedPartitionOffset));
    }

    @Test
    public void shouldNotReadIncompleteMessage()
    {
        int fragOffset = 0;

        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(-A_MSG_PAYLOAD_LENGTH);

        // when
        int fragmentsRead = subscription.pollFragments(logBufferPartition, mockFragmentHandler, 1, A_PARTITION_ID, fragOffset);

        // then
        assertThat(fragmentsRead).isEqualTo(0);
        // the fragment handler was not handed any fragments
        verifyNoMoreInteractions(mockFragmentHandler);
        // and the position was set but not increased
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, fragOffset));
    }

    private int nextFragmentOffset(int currentOffset)
    {
        return currentOffset + A_FRAGMENT_LENGTH;
    }

}

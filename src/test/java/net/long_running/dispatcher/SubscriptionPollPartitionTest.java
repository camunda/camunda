package net.long_running.dispatcher;

import static net.long_running.dispatcher.impl.log.DataFrameDescriptor.*;
import static org.mockito.Mockito.*;
import static net.long_running.dispatcher.impl.PositionUtil.*;
import static uk.co.real_logic.agrona.BitUtil.*;
import static org.assertj.core.api.Assertions.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import net.long_running.dispatcher.impl.Subscription;
import net.long_running.dispatcher.impl.log.LogBufferPartition;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.agrona.concurrent.status.Position;

public class SubscriptionPollPartitionTest
{
    static final int A_PARTITION_LENGTH = 1024;
    static final int A_MSG_PAYLOAD_LENGTH = 10;
    static final int A_FRAGMENT_LENGTH = align(A_MSG_PAYLOAD_LENGTH + HEADER_LENGTH, FRAME_ALIGNMENT);
    static final int A_PARTITION_ID = 10;

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
        logBufferPartition = new LogBufferPartition(dataBufferMock, metadataBufferMock);

        mockSubscriberPosition = mock(Position.class);
        mockFragmentHandler = mock(FragmentHandler.class);
        subscription = new Subscription(mockSubscriberPosition);
    }

    @Test
    public void shouldReadSingleFragment()
    {
        int fragOffset = 0;

        when(mockSubscriberPosition.get()).thenReturn(position(A_PARTITION_ID, fragOffset));
        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_MESSAGE);

        // when
        int fragmentsRead = subscription.pollPartition(logBufferPartition, mockFragmentHandler, 1, A_PARTITION_ID, fragOffset);

        // then
        assertThat(fragmentsRead).isEqualTo(1);
        // the fragment handler was handed one fragment
        verify(mockFragmentHandler).onFragment(eq(dataBufferMock), eq(messageOffset(fragOffset)), eq(A_MSG_PAYLOAD_LENGTH));
        verifyNoMoreInteractions(mockFragmentHandler);
        // and the position was increased by the fragment length
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, nextFragmentOffset(fragOffset)));
    }

    @Test
    public void shouldReadMultipleFragments()
    {
        int firstFragOffset = 0;
        int secondFragOffset = nextFragmentOffset(firstFragOffset);

        when(mockSubscriberPosition.get()).thenReturn(position(A_PARTITION_ID, firstFragOffset));

        when(dataBufferMock.getIntVolatile(firstFragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(firstFragOffset))).thenReturn(TYPE_MESSAGE);

        when(dataBufferMock.getIntVolatile(secondFragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(secondFragOffset))).thenReturn(TYPE_MESSAGE);

        // when
        int fragmentsRead = subscription.pollPartition(logBufferPartition, mockFragmentHandler, 2, A_PARTITION_ID, firstFragOffset);

        // then
        assertThat(fragmentsRead).isEqualTo(2);

        // the fragment handler was handed two fragments
        InOrder inOrder = inOrder(mockFragmentHandler);
        inOrder.verify(mockFragmentHandler).onFragment(eq(dataBufferMock), eq(messageOffset(firstFragOffset)), eq(A_MSG_PAYLOAD_LENGTH));
        inOrder.verify(mockFragmentHandler).onFragment(eq(dataBufferMock), eq(messageOffset(secondFragOffset)), eq(A_MSG_PAYLOAD_LENGTH));
        inOrder.verifyNoMoreInteractions();

        // and the position was increased by the fragment length
        int expectedPartitionOffset = nextFragmentOffset(secondFragOffset);
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID, expectedPartitionOffset));
    }

    @Test
    public void shouldRollOverPartitionOnPadding()
    {
        int fragOffset = 0;

        when(mockSubscriberPosition.get()).thenReturn(position(A_PARTITION_ID, fragOffset));
        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(A_MSG_PAYLOAD_LENGTH);
        when(dataBufferMock.getShort(typeOffset(fragOffset))).thenReturn(TYPE_PADDING);

        // when
        int fragmentsRead = subscription.pollPartition(logBufferPartition, mockFragmentHandler, 2, A_PARTITION_ID, fragOffset);

        // then
        assertThat(fragmentsRead).isEqualTo(0);
        // the fragment handler was not handed any fragments
        verifyNoMoreInteractions(mockFragmentHandler);
        // the position was set to the beginning of the next partition
        verify(mockSubscriberPosition).setOrdered(position(A_PARTITION_ID + 1, 0));
    }

    @Test
    public void shouldReadIncompleteMessage()
    {
        int fragOffset = 0;

        when(mockSubscriberPosition.get()).thenReturn(position(A_PARTITION_ID, fragOffset));
        when(dataBufferMock.getIntVolatile(fragOffset)).thenReturn(-A_MSG_PAYLOAD_LENGTH);

        // when
        int fragmentsRead = subscription.pollPartition(logBufferPartition, mockFragmentHandler, 1, A_PARTITION_ID, fragOffset);

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

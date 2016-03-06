package net.long_running.dispatcher;

import static org.mockito.Mockito.*;
import static uk.co.real_logic.agrona.BitUtil.*;
import static net.long_running.dispatcher.impl.PositionUtil.*;

import java.nio.charset.Charset;

import static net.long_running.dispatcher.impl.log.DataFrameDescriptor.*;
import static org.assertj.core.api.Assertions.*;

import org.junit.Before;
import org.junit.Test;

import net.long_running.dispatcher.impl.Subscription;
import net.long_running.dispatcher.impl.log.LogAppender;
import net.long_running.dispatcher.impl.log.LogBuffer;
import net.long_running.dispatcher.impl.log.LogBufferPartition;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.agrona.concurrent.status.Position;

public class DispatcherTest
{

    static final byte[] A_MSG_PAYLOAD = "some bytes".getBytes(Charset.forName("utf-8"));
    static final int A_MSG_PAYLOAD_LENGTH = A_MSG_PAYLOAD.length;
    static final int A_FRAGMENT_LENGTH = align(A_MSG_PAYLOAD_LENGTH + HEADER_LENGTH, FRAME_ALIGNMENT);
    static final UnsafeBuffer A_MSG = new UnsafeBuffer(A_MSG_PAYLOAD);
    static final int AN_INITIAL_PARTITION_ID = 0;
    static final int A_LOG_WINDOW_LENGTH = 128;
    static final int A_PARITION_SIZE = 1024;
    static final int A_STREAM_ID = 20;

    Dispatcher dispatcher;
    LogBuffer logBuffer;
    LogBufferPartition logBufferPartition0;
    LogBufferPartition logBufferPartition1;
    LogBufferPartition logBufferPartition2;
    LogAppender logAppender;
    Position publisherLimit;
    Position publisherPosition;
    Subscription subscription;
    FragmentHandler fragmentHandler;
    ClaimedFragment claimedFragment;

    @Before
    public void setup()
    {
        logBuffer = mock(LogBuffer.class);
        logBufferPartition0 = mock(LogBufferPartition.class);
        logBufferPartition1 = mock(LogBufferPartition.class);
        logBufferPartition2 = mock(LogBufferPartition.class);

        when(logBuffer.getInitialPartitionId()).thenReturn(AN_INITIAL_PARTITION_ID);
        when(logBuffer.getPartitionCount()).thenReturn(3);
        when(logBuffer.getPartitionSize()).thenReturn(A_PARITION_SIZE);
        when(logBuffer.getPartition(0)).thenReturn(logBufferPartition0);
        when(logBuffer.getPartition(1)).thenReturn(logBufferPartition1);
        when(logBuffer.getPartition(2)).thenReturn(logBufferPartition2);

        logAppender = mock(LogAppender.class);
        publisherLimit = mock(Position.class);
        publisherPosition = mock(Position.class);
        fragmentHandler = mock(FragmentHandler.class);
        subscription = mock(Subscription.class);
        claimedFragment = mock(ClaimedFragment.class);

        dispatcher = new Dispatcher(logBuffer,
                logAppender,
                publisherLimit,
                publisherPosition,
                new Position[] { null },
                A_LOG_WINDOW_LENGTH,
                null)
        {
            @Override
            protected Subscription createSubscription(Position subscriberPosition) {
                return subscription;
            }
        };
    }

    @Test
    public void shouldNotWriteBeyondPublisherLimit()
    {
        // given
        // position of 0,0
        when(logBuffer.getActivePartitionIdVolatile()).thenReturn(0);
        when(logBufferPartition0.getTailCounterVolatile()).thenReturn(0);
        // publisher limit of 0
        when(publisherLimit.getVolatile()).thenReturn(position(0, 0));

        // if
        long newPosition = dispatcher.offer(A_MSG, 0, A_MSG_PAYLOAD_LENGTH);

        // then
        assertThat(newPosition).isEqualTo(-1);

        verify(publisherLimit).getVolatile();
        verifyNoMoreInteractions(publisherLimit);
        verifyNoMoreInteractions(logAppender);
        verify(logBuffer).getActivePartitionIdVolatile();
        verify(logBuffer).getPartition(0);
        verify(logBufferPartition0).getTailCounterVolatile();
    }

    @Test
    public void shouldNotClaimBeyondPublisherLimit()
    {
        // given
        // position of 0,0
        when(logBuffer.getActivePartitionIdVolatile()).thenReturn(0);
        when(logBufferPartition0.getTailCounterVolatile()).thenReturn(0);
        // publisher limit of 0
        when(publisherLimit.getVolatile()).thenReturn(position(0, 0));

        // if
        long newPosition = dispatcher.claim(claimedFragment, A_MSG_PAYLOAD_LENGTH);

        // then
        assertThat(newPosition).isEqualTo(-1);

        verify(publisherLimit).getVolatile();
        verifyNoMoreInteractions(publisherLimit);
        verifyNoMoreInteractions(logAppender);
        verifyNoMoreInteractions(claimedFragment);
        verify(logBuffer).getActivePartitionIdVolatile();
        verify(logBuffer).getPartition(0);
        verify(logBufferPartition0).getTailCounterVolatile();
    }

    @Test
    public void shouldWriteUnfragmented()
    {
        // given
        // position is 0,0
        when(logBuffer.getActivePartitionIdVolatile()).thenReturn(0);
        when(logBufferPartition0.getTailCounterVolatile()).thenReturn(0);
        when(publisherLimit.getVolatile()).thenReturn(position(0, A_FRAGMENT_LENGTH));

        when(logAppender.appendFrame(logBufferPartition0, 0, A_MSG, 0, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID)).thenReturn(A_FRAGMENT_LENGTH);

        // if
        long newPosition = dispatcher.offer(A_MSG, 0, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID);

        // then
        assertThat(newPosition).isEqualTo(position(0, A_FRAGMENT_LENGTH));

        verify(logAppender).appendFrame(logBufferPartition0, 0, A_MSG, 0, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID);

        verify(publisherLimit).getVolatile();
        verify(publisherPosition).proposeMaxOrdered(position(0, A_FRAGMENT_LENGTH));

        verify(logBuffer).getActivePartitionIdVolatile();
        verify(logBuffer).getPartition(0);
        verify(logBufferPartition0).getTailCounterVolatile();

    }

    @Test
    public void shouldClaimFragment()
    {
        // given
        // position is 0,0
        when(logBuffer.getActivePartitionIdVolatile()).thenReturn(0);
        when(logBufferPartition0.getTailCounterVolatile()).thenReturn(0);
        when(publisherLimit.getVolatile()).thenReturn(position(0, A_FRAGMENT_LENGTH));

        when(logAppender.claim(logBufferPartition0, 0, claimedFragment, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID)).thenReturn(A_FRAGMENT_LENGTH);

        // if
        long newPosition = dispatcher.claim(claimedFragment, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID);

        // then
        assertThat(newPosition).isEqualTo(position(0, A_FRAGMENT_LENGTH));

        verify(logAppender).claim(logBufferPartition0, 0, claimedFragment, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID);

        verify(publisherLimit).getVolatile();
        verify(publisherPosition).proposeMaxOrdered(position(0, A_FRAGMENT_LENGTH));

        verify(logBuffer).getActivePartitionIdVolatile();
        verify(logBuffer).getPartition(0);
        verify(logBufferPartition0).getTailCounterVolatile();

    }

    @Test
    public void shouldRollPartitionOnPartitionFilled()
    {
        // given
        // position is 0,0
        when(logBuffer.getActivePartitionIdVolatile()).thenReturn(0);
        when(logBufferPartition0.getTailCounterVolatile()).thenReturn(0);
        when(publisherLimit.getVolatile()).thenReturn(position(0, A_FRAGMENT_LENGTH));

        when(logAppender.appendFrame(logBufferPartition0, 0, A_MSG, 0, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID)).thenReturn(-2);

        // if
        long newPosition = dispatcher.offer(A_MSG, 0, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID);

        // then
        assertThat(newPosition).isEqualTo(-2);

        verify(publisherLimit).getVolatile();
        verify(publisherPosition).proposeMaxOrdered(-2);

        verify(logBuffer).getActivePartitionIdVolatile();
        verify(logBuffer).getPartition(0);
        verify(logBuffer).onActiveParitionFilled(0);

        verify(logBufferPartition0).getTailCounterVolatile();

    }

    @Test
    public void shouldIgnoreWriteIfPastPartitionEnd()
    {
        // given
        // position is 0,0
        when(logBuffer.getActivePartitionIdVolatile()).thenReturn(0);
        when(logBufferPartition0.getTailCounterVolatile()).thenReturn(0);
        when(publisherLimit.getVolatile()).thenReturn(position(0, A_FRAGMENT_LENGTH));

        when(logAppender.appendFrame(logBufferPartition0, 0, A_MSG, 0, A_MSG_PAYLOAD_LENGTH, A_STREAM_ID)).thenReturn(-1);

        // if
        long newPosition = dispatcher.offer(A_MSG, 0, A_MSG_PAYLOAD_LENGTH);

        // then
        assertThat(newPosition).isEqualTo(-1);

        verify(publisherLimit).getVolatile();
        verify(publisherPosition).proposeMaxOrdered(-1);

        verify(logBuffer).getActivePartitionIdVolatile();
        verify(logBuffer).getPartition(0);
        verify(logBuffer, times(0)).onActiveParitionFilled(anyInt());

        verify(logBufferPartition0).getTailCounterVolatile();
    }

    @Test
    public void shouldReadFromPartition()
    {
        // given
        when(subscription.getPosition()).thenReturn(0l);
        when(publisherPosition.get()).thenReturn(position(0, A_FRAGMENT_LENGTH));

        when(subscription.pollPartition(logBufferPartition0, fragmentHandler, 2, 0, 0)).thenReturn(1);

        // if
        int fragmentsRead = dispatcher.poll(fragmentHandler, 2);

        // then
        assertThat(fragmentsRead).isEqualTo(1);
        verify(subscription).getPosition();
        verify(subscription).pollPartition(logBufferPartition0, fragmentHandler, 2, 0, 0);
    }

    @Test
    public void shouldNotReadBeyondPublisherPosition()
    {
        // given
        when(subscription.getPosition()).thenReturn(0l);
        when(publisherPosition.get()).thenReturn(0l);

        // if
        int fragmentsRead = dispatcher.poll(fragmentHandler, 1);

        // then
        assertThat(fragmentsRead).isEqualTo(0);

        verify(subscription).getPosition();
        verifyNoMoreInteractions(subscription);
    }

    @Test
    public void shouldUpdatePublisherLimit()
    {
        when(subscription.getPosition()).thenReturn(position(10, 100));

        dispatcher.updatePublisherLimit();

        verify(publisherLimit).proposeMaxOrdered(position(10, 100 + A_LOG_WINDOW_LENGTH));
    }

    @Test
    public void shouldUpdatePublisherLimitToNextPartition()
    {
        when(subscription.getPosition()).thenReturn(position(10, A_PARITION_SIZE - A_LOG_WINDOW_LENGTH));

        dispatcher.updatePublisherLimit();

        verify(publisherLimit).proposeMaxOrdered(position(11, A_LOG_WINDOW_LENGTH));
    }

}

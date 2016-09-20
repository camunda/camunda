package org.camunda.tngp.dispatcher;

import static org.agrona.BitUtil.align;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.dispatcher.impl.PositionUtil.position;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.FRAME_ALIGNMENT;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.Charset;

import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.Position;
import org.camunda.tngp.dispatcher.impl.DispatcherContext;
import org.camunda.tngp.dispatcher.impl.log.LogBuffer;
import org.camunda.tngp.dispatcher.impl.log.LogBufferAppender;
import org.camunda.tngp.dispatcher.impl.log.LogBufferPartition;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DispatcherPipelineModeTest
{

    static final byte[] A_MSG_PAYLOAD = "some bytes".getBytes(Charset.forName("utf-8"));
    static final int A_MSG_PAYLOAD_LENGTH = A_MSG_PAYLOAD.length;
    static final int A_FRAGMENT_LENGTH = align(A_MSG_PAYLOAD_LENGTH + HEADER_LENGTH, FRAME_ALIGNMENT);
    static final UnsafeBuffer A_MSG = new UnsafeBuffer(A_MSG_PAYLOAD);
    static final int AN_INITIAL_PARTITION_ID = 0;
    static final int A_LOG_WINDOW_LENGTH = 128;
    static final int A_PARITION_SIZE = 1024;
    static final int A_STREAM_ID = 20;
    static final String[] SUBSCRIPTION_NAMES = new String[] {"s1", "s2"};

    Dispatcher dispatcher;
    LogBuffer logBuffer;
    LogBufferPartition logBufferPartition0;
    LogBufferPartition logBufferPartition1;
    LogBufferPartition logBufferPartition2;
    LogBufferAppender logAppender;
    Position publisherLimit;
    Position publisherPosition;
    FragmentHandler fragmentHandler;
    ClaimedFragment claimedFragment;
    Position subscriberPosition;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

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

        logAppender = mock(LogBufferAppender.class);
        publisherLimit = mock(Position.class);
        publisherPosition = mock(Position.class);
        fragmentHandler = mock(FragmentHandler.class);
        claimedFragment = mock(ClaimedFragment.class);
        subscriberPosition = mock(Position.class);

        dispatcher = new Dispatcher(logBuffer,
                logAppender,
                publisherLimit,
                publisherPosition,
                A_LOG_WINDOW_LENGTH,
                SUBSCRIPTION_NAMES,
                Dispatcher.MODE_PIPELINE,
                mock(DispatcherContext.class),
                "test")
        {
            @Override
            protected Subscription newSubscription(int subscriptionId, String subscriptionName)
            {
                return spy(super.newSubscription(subscriptionId, subscriptionName));
            }
        };
    }

    @Test
    public void shouldGetPredefinedSubscriptions()
    {
        final Subscription subscription1 = dispatcher.getSubscriptionByName("s1");
        assertThat(subscription1).isNotNull();
        assertThat(subscription1.getName()).isEqualTo("s1");
        assertThat(subscription1.getId()).isEqualTo(0);

        final Subscription subscription2 = dispatcher.getSubscriptionByName("s2");
        assertThat(subscription2).isNotNull();
        assertThat(subscription2.getName()).isEqualTo("s2");
        assertThat(subscription2.getId()).isEqualTo(1);

        assertThat(dispatcher.getSubscriptionByName("nonExisting")).isNull();
    }

    @Test
    public void shouldNotOpenSubscription()
    {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Cannot open subscriptions in pipelining mode");

        dispatcher.doOpenSubscription("new");
    }

    @Test
    public void shouldNotCloseSubscription()
    {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Cannot close subscriptions in pipelining mode");

        final Subscription subscription = dispatcher.getSubscriptionByName("s1");
        dispatcher.doCloseSubscription(subscription);
    }

    @Test
    public void shouldNotReadBeyondPreviousSubscription()
    {
        // given
        final Subscription subscription1 = dispatcher.getSubscriptionByName("s1");
        final Subscription subscription2 = dispatcher.getSubscriptionByName("s2");

        when(subscription1.getPosition()).thenReturn(position(0, 0));
        when(subscription2.getPosition()).thenReturn(position(0, 0));

        when(publisherPosition.get()).thenReturn(position(0, A_FRAGMENT_LENGTH));

        // when
        final int fragmentsRead = subscription2.poll(fragmentHandler, 1);

        // then
        assertThat(fragmentsRead).isEqualTo(0);
    }

    @Test
    public void shouldUpdatePublisherLimit()
    {
        // given
        final Subscription subscription1 = dispatcher.getSubscriptionByName("s1");
        final Subscription subscription2 = dispatcher.getSubscriptionByName("s2");

        when(subscription1.getPosition()).thenReturn(position(0, 2 * A_FRAGMENT_LENGTH));
        when(subscription2.getPosition()).thenReturn(position(0, A_FRAGMENT_LENGTH));

        // when
        dispatcher.updatePublisherLimit();

        // then
        verify(publisherLimit).proposeMaxOrdered(position(0, A_FRAGMENT_LENGTH + A_LOG_WINDOW_LENGTH));
    }

}

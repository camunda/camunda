package org.camunda.tngp.broker.taskqueue.request.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.taskqueue.MockTaskQueueContext;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.protocol.taskqueue.ProvideSubscriptionCreditsDecoder;
import org.camunda.tngp.protocol.taskqueue.ProvideSubscriptionCreditsReader;
import org.camunda.tngp.transport.singlemessage.DataFramePool;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ProvideSubscriptionCreditsHandlerTest
{

    public static final long CREDITS = 123456L;

    protected TaskQueueContext taskQueueContext;
    protected LockTasksOperator taskOperator;

    @Mock
    protected ProvideSubscriptionCreditsReader requestReader;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        taskOperator = new LockTasksOperator(
                mock(Bytes2LongHashIndex.class),
                mock(LogStreamReader.class),
                mock(LogWriter.class),
                mock(DataFramePool.class),
                1);
        taskQueueContext = new MockTaskQueueContext();
        taskQueueContext.setLockedTasksOperator(taskOperator);
    }

    @Test
    public void shouldAddToCredits()
    {
        // given
        final TaskSubscription subscription =
                taskOperator.openSubscription(123, 1234, 12345L, CREDITS, new UnsafeBuffer(new byte[1]));

        final ProvideSubscriptionCreditsHandler handler = new ProvideSubscriptionCreditsHandler();
        handler.requestReader = requestReader;

        when(requestReader.consumerId()).thenReturn(1234);
        when(requestReader.credits()).thenReturn(4L);
        when(requestReader.subscriptionId()).thenReturn(subscription.getId());

        // when
        handler.onDataFrame(taskQueueContext, mock(DirectBuffer.class), 0, 0);

        // then
        assertThat(subscription.getCredits()).isEqualTo(CREDITS + 4L);
    }

    @Test
    public void shouldIgnoreOnMismatchingConsumerId()
    {
        // given
        final TaskSubscription subscription =
                taskOperator.openSubscription(123, 1234, 12345L, CREDITS, new UnsafeBuffer(new byte[1]));

        final ProvideSubscriptionCreditsHandler handler = new ProvideSubscriptionCreditsHandler();
        handler.requestReader = requestReader;

        when(requestReader.consumerId()).thenReturn(99);
        when(requestReader.credits()).thenReturn(4L);
        when(requestReader.subscriptionId()).thenReturn(subscription.getId());

        // when
        handler.onDataFrame(taskQueueContext, mock(DirectBuffer.class), 0, 0);

        // then
        assertThat(subscription.getCredits()).isEqualTo(CREDITS);
    }

    @Test
    public void shouldIgnoreOnNullSubscriptionId()
    {
        // given
        final TaskSubscription subscription =
                taskOperator.openSubscription(123, 1234, 12345L, CREDITS, new UnsafeBuffer(new byte[1]));

        final ProvideSubscriptionCreditsHandler handler = new ProvideSubscriptionCreditsHandler();
        handler.requestReader = requestReader;

        when(requestReader.consumerId()).thenReturn(1234);
        when(requestReader.credits()).thenReturn(4L);
        when(requestReader.subscriptionId()).thenReturn(ProvideSubscriptionCreditsDecoder.subscriptionIdNullValue());

        // when
        handler.onDataFrame(taskQueueContext, mock(DirectBuffer.class), 0, 0);

        // then
        assertThat(subscription.getCredits()).isEqualTo(CREDITS);
    }

    @Test
    public void shouldIgnoreOnMissingSubscription()
    {
        // given
        final ProvideSubscriptionCreditsHandler handler = new ProvideSubscriptionCreditsHandler();
        handler.requestReader = requestReader;

        when(requestReader.consumerId()).thenReturn(1234);
        when(requestReader.credits()).thenReturn(4L);
        when(requestReader.subscriptionId()).thenReturn(1L);

        // when
        handler.onDataFrame(taskQueueContext, mock(DirectBuffer.class), 0, 0);

        // then nothing bad has happened
    }

    @Test
    public void shouldIgnoreOnCreditsOverflow()
    {
        // given
        final long maxCredits = Integer.MAX_VALUE * 2L; // uint32

        final TaskSubscription subscription =
                taskOperator.openSubscription(123, 1234, 12345L, maxCredits, new UnsafeBuffer(new byte[1]));

        final ProvideSubscriptionCreditsHandler handler = new ProvideSubscriptionCreditsHandler();
        handler.requestReader = requestReader;

        when(requestReader.consumerId()).thenReturn(1234);
        when(requestReader.credits()).thenReturn(1L);
        when(requestReader.subscriptionId()).thenReturn(subscription.getId());

        // when
        handler.onDataFrame(taskQueueContext, mock(DirectBuffer.class), 0, 0);

        // then
        assertThat(subscription.getCredits()).isEqualTo(maxCredits);
    }

}

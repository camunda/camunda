package org.camunda.tngp.broker.taskqueue.request.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.taskqueue.TaskErrors;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.taskqueue.subscription.LockTasksOperator;
import org.camunda.tngp.broker.taskqueue.subscription.TaskSubscription;
import org.camunda.tngp.broker.test.util.BufferWriterUtil;
import org.camunda.tngp.protocol.error.ErrorReader;
import org.camunda.tngp.protocol.taskqueue.CloseTaskSubscriptionDecoder;
import org.camunda.tngp.protocol.taskqueue.CloseTaskSubscriptionRequestReader;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CloseTaskSubscriptionHandlerTest
{

    @Mock
    protected DirectBuffer message;

    @Mock
    protected DeferredResponse response;

    @Mock
    protected CloseTaskSubscriptionRequestReader requestReader;

    @Mock
    protected LockTasksOperator taskOperator;

    @Mock
    protected TaskQueueContext taskQueueContext;

    @Captor
    protected ArgumentCaptor<BufferWriter> captor;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        when(taskQueueContext.getLockedTasksOperator()).thenReturn(taskOperator);
    }

    @Test
    public void shouldCloseSubscription()
    {
        // given
        final CloseTaskSubscriptionHandler handler = new CloseTaskSubscriptionHandler();
        handler.requestReader = requestReader;

        when(requestReader.consumerId()).thenReturn(34);
        when(requestReader.subscriptionId()).thenReturn(123L);

        final TaskSubscription subscription = new TaskSubscription(123L);
        subscription.setConsumerId(34);
        when(taskOperator.getSubscription(123L)).thenReturn(subscription);

        // when
        handler.onRequest(taskQueueContext, message, 0, 0, response);

        // then
        verify(taskOperator).removeSubscription(subscription);
    }

    @Test
    public void shouldWriteErrorOnMismatchingConsumerId()
    {
        // given
        final CloseTaskSubscriptionHandler handler = new CloseTaskSubscriptionHandler();
        handler.requestReader = requestReader;

        when(requestReader.consumerId()).thenReturn(34);
        when(requestReader.subscriptionId()).thenReturn(123L);

        final TaskSubscription subscription = new TaskSubscription(123L);
        subscription.setConsumerId(45);
        when(taskOperator.getSubscription(123L)).thenReturn(subscription);

        // when
        handler.onRequest(taskQueueContext, message, 0, 0, response);

        // then
        verify(response).allocateAndWrite(captor.capture());

        final ErrorReader errorReader = BufferWriterUtil.wrap(captor.getValue(), ErrorReader.class);
        assertThat(errorReader.componentCode()).isEqualTo(TaskErrors.COMPONENT_CODE);
        assertThat(errorReader.detailCode()).isEqualTo(TaskErrors.CLOSE_SUBSCRIPTION_ERROR);
        assertThat(errorReader.errorMessage()).isEqualTo("Subscription does not belong to provided consumer");

        verify(taskOperator, never()).removeSubscription(any());
    }

    @Test
    public void shouldWriteErrorOnNonExistingSubscription()
    {
        // given
        final CloseTaskSubscriptionHandler handler = new CloseTaskSubscriptionHandler();
        handler.requestReader = requestReader;

        when(requestReader.consumerId()).thenReturn(34);
        when(requestReader.subscriptionId()).thenReturn(123L);

        // when
        handler.onRequest(taskQueueContext, message, 0, 0, response);

        // then
        verify(response).allocateAndWrite(captor.capture());

        final ErrorReader errorReader = BufferWriterUtil.wrap(captor.getValue(), ErrorReader.class);
        assertThat(errorReader.componentCode()).isEqualTo(TaskErrors.COMPONENT_CODE);
        assertThat(errorReader.detailCode()).isEqualTo(TaskErrors.CLOSE_SUBSCRIPTION_ERROR);
        assertThat(errorReader.errorMessage()).isEqualTo("Subscription does not exist");

        verify(taskOperator, never()).removeSubscription(any());
    }

    @Test
    public void shouldWriteErrorOnNullConsumerId()
    {
        // given
        final CloseTaskSubscriptionHandler handler = new CloseTaskSubscriptionHandler();
        handler.requestReader = requestReader;

        when(requestReader.consumerId()).thenReturn(CloseTaskSubscriptionDecoder.consumerIdNullValue());
        when(requestReader.subscriptionId()).thenReturn(123L);

        // when
        handler.onRequest(taskQueueContext, message, 0, 0, response);

        // then
        verify(response).allocateAndWrite(captor.capture());

        final ErrorReader errorReader = BufferWriterUtil.wrap(captor.getValue(), ErrorReader.class);
        assertThat(errorReader.componentCode()).isEqualTo(TaskErrors.COMPONENT_CODE);
        assertThat(errorReader.detailCode()).isEqualTo(TaskErrors.CLOSE_SUBSCRIPTION_ERROR);
        assertThat(errorReader.errorMessage()).isEqualTo("Consumer id not provided");

        verify(taskOperator, never()).removeSubscription(any());
    }

    @Test
    public void shouldWriteErrorOnNullSubscriptionId()
    {
        // given
        final CloseTaskSubscriptionHandler handler = new CloseTaskSubscriptionHandler();
        handler.requestReader = requestReader;

        when(requestReader.consumerId()).thenReturn(34);
        when(requestReader.subscriptionId()).thenReturn(CloseTaskSubscriptionDecoder.subscriptionIdNullValue());

        // when
        handler.onRequest(taskQueueContext, message, 0, 0, response);

        // then
        verify(response).allocateAndWrite(captor.capture());

        final ErrorReader errorReader = BufferWriterUtil.wrap(captor.getValue(), ErrorReader.class);
        assertThat(errorReader.componentCode()).isEqualTo(TaskErrors.COMPONENT_CODE);
        assertThat(errorReader.detailCode()).isEqualTo(TaskErrors.CLOSE_SUBSCRIPTION_ERROR);
        assertThat(errorReader.errorMessage()).isEqualTo("Subscription id not provided");

        verify(taskOperator, never()).removeSubscription(any());
    }
}

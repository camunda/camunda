package org.camunda.tngp.broker.taskqueue.request.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.test.util.BufferWriterUtil;
import org.camunda.tngp.protocol.error.ErrorReader;
import org.camunda.tngp.protocol.taskqueue.CreateTaskSubscriptionEncoder;
import org.camunda.tngp.protocol.taskqueue.CreateTaskSubscriptionRequestReader;
import org.camunda.tngp.protocol.taskqueue.TaskSubscriptionReader;
import org.camunda.tngp.protocol.wf.Constants;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CreateTaskSubscriptionHandlerTest
{

    public static final byte[] TASK_TYPE = "foo".getBytes(StandardCharsets.UTF_8);
    public static final byte[] OVERLONG_TASK_TYPE = new byte[Constants.TASK_TYPE_MAX_LENGTH + 1];

    @Mock
    protected UnsafeBuffer message;

    @Mock
    protected DeferredResponse response;

    @Mock
    protected LockTasksOperator taskOperator;

    @Mock
    protected TaskQueueContext taskQueueContext;

    @Captor
    protected ArgumentCaptor<BufferWriter> captor;

    @Mock
    protected CreateTaskSubscriptionRequestReader requestReader;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        when(taskQueueContext.getLockedTasksOperator()).thenReturn(taskOperator);
    }

    @Test
    public void shouldCreateSubscription()
    {
        // given
        final CreateTaskSubscriptionHandler handler = new CreateTaskSubscriptionHandler();

        final UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(TASK_TYPE);

        when(requestReader.consumerId()).thenReturn(123);
        when(requestReader.lockDuration()).thenReturn(888888L);
        when(requestReader.initialCredits()).thenReturn(4242L);
        when(requestReader.taskType()).thenReturn(taskTypeBuffer);
        handler.requestReader = requestReader;

        when(response.getChannelId()).thenReturn(999);

        final TaskSubscription subscription = mock(TaskSubscription.class);
        when(subscription.getId()).thenReturn(7777L);
        when(taskOperator.openSubscription(999, 123, 888888L, 4242L, taskTypeBuffer)).thenReturn(subscription);

        // when
        handler.onRequest(taskQueueContext, message, 0, 0, response);

        // then
        verify(response).allocateAndWrite(captor.capture());
        final TaskSubscriptionReader reader = BufferWriterUtil.wrap(captor.getValue(), TaskSubscriptionReader.class);
        assertThat(reader.id()).isEqualTo(7777L);

    }

    @Test
    public void shouldWriteErrorOnNullConsumerId()
    {
        // given
        final CreateTaskSubscriptionHandler handler = new CreateTaskSubscriptionHandler();

        final UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(TASK_TYPE);

        when(requestReader.consumerId()).thenReturn(CreateTaskSubscriptionEncoder.consumerIdNullValue());
        when(requestReader.lockDuration()).thenReturn(888888L);
        when(requestReader.initialCredits()).thenReturn(4242L);
        when(requestReader.taskType()).thenReturn(taskTypeBuffer);
        handler.requestReader = requestReader;

        // when
        handler.onRequest(taskQueueContext, message, 0, 0, response);

        // then
        verify(taskOperator, never()).openSubscription(anyInt(), anyInt(), anyLong(), anyLong(), any());
        verify(response).allocateAndWrite(captor.capture());
        final ErrorReader reader = BufferWriterUtil.wrap(captor.getValue(), ErrorReader.class);
        assertThat(reader.componentCode()).isEqualTo(TaskErrors.COMPONENT_CODE);
        assertThat(reader.detailCode()).isEqualTo(TaskErrors.CREATE_SUBSCRIPTION_ERROR);
        assertThat(reader.errorMessage()).isEqualTo("Consumer id not provided");
    }

    @Test
    public void shouldWriteErrorOnNullLockDuration()
    {
        // given
        final CreateTaskSubscriptionHandler handler = new CreateTaskSubscriptionHandler();

        final UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(TASK_TYPE);

        when(requestReader.consumerId()).thenReturn(123);
        when(requestReader.lockDuration()).thenReturn(CreateTaskSubscriptionEncoder.lockDurationNullValue());
        when(requestReader.initialCredits()).thenReturn(4242L);
        when(requestReader.taskType()).thenReturn(taskTypeBuffer);
        handler.requestReader = requestReader;

        // when
        handler.onRequest(taskQueueContext, message, 0, 0, response);

        // then
        verify(taskOperator, never()).openSubscription(anyInt(), anyInt(), anyLong(), anyLong(), any());
        verify(response).allocateAndWrite(captor.capture());
        final ErrorReader reader = BufferWriterUtil.wrap(captor.getValue(), ErrorReader.class);
        assertThat(reader.componentCode()).isEqualTo(TaskErrors.COMPONENT_CODE);
        assertThat(reader.detailCode()).isEqualTo(TaskErrors.CREATE_SUBSCRIPTION_ERROR);
        assertThat(reader.errorMessage()).isEqualTo("Lock duration not provided");
    }

    @Test
    public void shouldWriteErrorOnNullCredits()
    {
        // given
        final CreateTaskSubscriptionHandler handler = new CreateTaskSubscriptionHandler();

        final UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(TASK_TYPE);

        when(requestReader.consumerId()).thenReturn(123);
        when(requestReader.lockDuration()).thenReturn(123123L);
        when(requestReader.initialCredits()).thenReturn(CreateTaskSubscriptionEncoder.initialCreditsNullValue());
        when(requestReader.taskType()).thenReturn(taskTypeBuffer);
        handler.requestReader = requestReader;

        // when
        handler.onRequest(taskQueueContext, message, 0, 0, response);

        // then
        verify(taskOperator, never()).openSubscription(anyInt(), anyInt(), anyLong(), anyLong(), any());
        verify(response).allocateAndWrite(captor.capture());
        final ErrorReader reader = BufferWriterUtil.wrap(captor.getValue(), ErrorReader.class);
        assertThat(reader.componentCode()).isEqualTo(TaskErrors.COMPONENT_CODE);
        assertThat(reader.detailCode()).isEqualTo(TaskErrors.CREATE_SUBSCRIPTION_ERROR);
        assertThat(reader.errorMessage()).isEqualTo("Initial credits not provided");
    }

    @Test
    public void shouldWriterErrorOnOverlongTaskType()
    {
        // given
        final CreateTaskSubscriptionHandler handler = new CreateTaskSubscriptionHandler();

        final UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(OVERLONG_TASK_TYPE);

        when(requestReader.consumerId()).thenReturn(123);
        when(requestReader.lockDuration()).thenReturn(123123L);
        when(requestReader.initialCredits()).thenReturn(34L);
        when(requestReader.taskType()).thenReturn(taskTypeBuffer);
        handler.requestReader = requestReader;

        // when
        handler.onRequest(taskQueueContext, message, 0, 0, response);

        // then
        verify(taskOperator, never()).openSubscription(anyInt(), anyInt(), anyLong(), anyLong(), any());
        verify(response).allocateAndWrite(captor.capture());
        final ErrorReader reader = BufferWriterUtil.wrap(captor.getValue(), ErrorReader.class);
        assertThat(reader.componentCode()).isEqualTo(TaskErrors.COMPONENT_CODE);
        assertThat(reader.detailCode()).isEqualTo(TaskErrors.CREATE_SUBSCRIPTION_ERROR);
        assertThat(reader.errorMessage()).isEqualTo("Provided task type exceeds maximum length " + Constants.TASK_TYPE_MAX_LENGTH);

    }
}

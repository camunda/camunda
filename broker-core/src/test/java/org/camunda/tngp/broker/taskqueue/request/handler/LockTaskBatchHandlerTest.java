package org.camunda.tngp.broker.taskqueue.request.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.taskqueue.MockTaskQueueContext;
import org.camunda.tngp.broker.taskqueue.PollAndLockTaskRequestReader;
import org.camunda.tngp.broker.taskqueue.TaskErrors;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.taskqueue.subscription.LockTasksOperator;
import org.camunda.tngp.broker.taskqueue.subscription.TaskSubscription;
import org.camunda.tngp.broker.test.util.BufferMatcher;
import org.camunda.tngp.broker.test.util.BufferWriterUtil;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.protocol.error.ErrorReader;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchEncoder;
import org.camunda.tngp.protocol.taskqueue.PollAndLockTasksEncoder;
import org.camunda.tngp.protocol.wf.Constants;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LockTaskBatchHandlerTest
{

    @Mock
    protected DirectBuffer requestBuffer;

    @Mock
    protected DeferredResponse response;

    @Mock
    protected PollAndLockTaskRequestReader requestReader;

    @Mock
    protected LockTasksOperator lockTasksOperator;

    @Captor
    protected ArgumentCaptor<BufferWriter> captor;

    protected static final byte[] TASK_TYPE = "ladida".getBytes(StandardCharsets.UTF_8);
    protected static final int TASK_TYPE_HASH = TaskTypeHash.hashCode(TASK_TYPE, TASK_TYPE.length);

    protected TaskQueueContext taskContext;
    protected StubLogWriter logWriter;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        taskContext = new MockTaskQueueContext();
        logWriter = new StubLogWriter();
        taskContext.setLogWriter(logWriter);
        taskContext.setLockedTasksOperator(lockTasksOperator);
    }

    @Test
    public void shouldOpenAdhocSubscription()
    {
        // given
        final LockTaskBatchHandler handler = new LockTaskBatchHandler();
        when(requestReader.consumerId()).thenReturn(123);
        when(requestReader.lockTime()).thenReturn(1234L);
        when(requestReader.maxTasks()).thenReturn(5);
        when(requestReader.taskType()).thenReturn(new UnsafeBuffer(TASK_TYPE));
        handler.requestReader = requestReader;

        final TaskSubscription taskSubscription = mock(TaskSubscription.class);
        when(lockTasksOperator.openAdhocSubscription(any(), anyInt(), anyLong(), anyLong(), any()))
            .thenReturn(taskSubscription);

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        assertThat(logWriter.size()).isEqualTo(0);
        verify(lockTasksOperator).openAdhocSubscription(
                eq(response),
                eq(123),
                eq(1234L),
                eq(5L),
                argThat(BufferMatcher.hasBytes(TASK_TYPE)));

        verify(response).defer();
    }

    @Test
    public void shouldWriteErrorResponseOnOverlongTaskType()
    {
        // given
        final LockTaskBatchHandler handler = new LockTaskBatchHandler();
        when(requestReader.consumerId()).thenReturn(123);
        when(requestReader.lockTime()).thenReturn(1234L);
        when(requestReader.maxTasks()).thenReturn(5);
        when(requestReader.taskType()).thenReturn(new UnsafeBuffer(new byte[Constants.WF_DEF_KEY_MAX_LENGTH + 1]));

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        handler.requestReader = requestReader;

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response);
        inOrder.verify(response).allocateAndWrite(captor.capture());
        inOrder.verify(response).commit();
        verifyNoMoreInteractions(response);

        final ErrorReader reader = new ErrorReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);

        assertThat(reader.componentCode()).isEqualTo(TaskErrors.COMPONENT_CODE);
        assertThat(reader.detailCode()).isEqualTo(TaskErrors.LOCK_TASKS_ERROR);
        assertThat(reader.errorMessage()).isEqualTo("Task type is too long");

        assertThat(logWriter.size()).isEqualTo(0);
    }

    @Test
    public void shouldWriteErrorResponseOnMissingConsumerId()
    {
        // given
        final LockTaskBatchHandler handler = new LockTaskBatchHandler();
        when(requestReader.consumerId()).thenReturn(LockedTaskBatchEncoder.consumerIdNullValue());
        when(requestReader.lockTime()).thenReturn(1234L);
        when(requestReader.maxTasks()).thenReturn(5);
        when(requestReader.taskType()).thenReturn(new UnsafeBuffer(new byte[Constants.WF_DEF_KEY_MAX_LENGTH + 1]));

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        handler.requestReader = requestReader;

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response);
        inOrder.verify(response).allocateAndWrite(captor.capture());
        inOrder.verify(response).commit();
        verifyNoMoreInteractions(response);

        final ErrorReader reader = new ErrorReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);

        assertThat(reader.componentCode()).isEqualTo(TaskErrors.COMPONENT_CODE);
        assertThat(reader.detailCode()).isEqualTo(TaskErrors.LOCK_TASKS_ERROR);
        assertThat(reader.errorMessage()).isEqualTo("Consumer id is required");

        assertThat(logWriter.size()).isEqualTo(0);
    }

    @Test
    public void shouldWriteErrorResponseOnMissingLockTimeout()
    {
        // given
        final LockTaskBatchHandler handler = new LockTaskBatchHandler();
        when(requestReader.consumerId()).thenReturn(123);
        when(requestReader.lockTime()).thenReturn(PollAndLockTasksEncoder.lockTimeNullValue());
        when(requestReader.maxTasks()).thenReturn(5);
        when(requestReader.taskType()).thenReturn(new UnsafeBuffer(new byte[Constants.WF_DEF_KEY_MAX_LENGTH + 1]));

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        handler.requestReader = requestReader;

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response);
        inOrder.verify(response).allocateAndWrite(captor.capture());
        inOrder.verify(response).commit();
        verifyNoMoreInteractions(response);

        final ErrorReader reader = new ErrorReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);

        assertThat(reader.componentCode()).isEqualTo(TaskErrors.COMPONENT_CODE);
        assertThat(reader.detailCode()).isEqualTo(TaskErrors.LOCK_TASKS_ERROR);
        assertThat(reader.errorMessage()).isEqualTo("Lock time is required");

        assertThat(logWriter.size()).isEqualTo(0);
    }

    @Test
    public void shouldWriteErrorResponseOnMissingTaskType()
    {
        // given
        final LockTaskBatchHandler handler = new LockTaskBatchHandler();
        when(requestReader.consumerId()).thenReturn(123);
        when(requestReader.lockTime()).thenReturn(1234L);
        when(requestReader.maxTasks()).thenReturn(5);
        when(requestReader.taskType()).thenReturn(new UnsafeBuffer(0, 0));

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        handler.requestReader = requestReader;

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response);
        inOrder.verify(response).allocateAndWrite(captor.capture());
        inOrder.verify(response).commit();
        verifyNoMoreInteractions(response);

        final ErrorReader reader = new ErrorReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);

        assertThat(reader.componentCode()).isEqualTo(TaskErrors.COMPONENT_CODE);
        assertThat(reader.detailCode()).isEqualTo(TaskErrors.LOCK_TASKS_ERROR);
        assertThat(reader.errorMessage()).isEqualTo("Task type is required");

        assertThat(logWriter.size()).isEqualTo(0);
    }


}

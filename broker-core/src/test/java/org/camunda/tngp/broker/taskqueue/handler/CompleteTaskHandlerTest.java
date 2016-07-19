package org.camunda.tngp.broker.taskqueue.handler;

import static org.camunda.tngp.broker.test.util.BufferMatcher.hasBytes;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.taskqueue.CompleteTaskRequestReader;
import org.camunda.tngp.broker.taskqueue.MockTaskQueueContext;
import org.camunda.tngp.broker.taskqueue.TaskErrors;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.protocol.error.ErrorReader;
import org.camunda.tngp.protocol.taskqueue.CompleteTaskEncoder;
import org.camunda.tngp.protocol.taskqueue.SingleTaskAckResponseReader;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class CompleteTaskHandlerTest
{

    @Mock
    protected DirectBuffer message;

    @Mock
    protected DeferredResponse response;

    @Mock
    protected CompleteTaskRequestReader requestReader;

    @Mock
    protected Long2LongHashIndex taskInstanceIndex;


    protected TaskQueueContext taskContext;

    protected static final byte[] PAYLOAD = "cam".getBytes(StandardCharsets.UTF_8);
    protected static final byte[] TASK_TYPE = "unda".getBytes(StandardCharsets.UTF_8);
    protected static final int TASK_TYPE_HASH = TaskTypeHash.hashCode(TASK_TYPE, TASK_TYPE.length);


    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        taskContext = new MockTaskQueueContext();

        final HashIndexManager<Long2LongHashIndex> hashIndexManager = mock(HashIndexManager.class);
        when(hashIndexManager.getIndex()).thenReturn(taskInstanceIndex);

        taskContext.setLockedTaskInstanceIndex(hashIndexManager);

    }

    @Test
    public void shouldCompleteTask()
    {
        // given
        final CompleteTaskHandler handler = new CompleteTaskHandler();
        final LogWriter logWriter = taskContext.getLogWriter();

        when(requestReader.consumerId()).thenReturn(765);
        when(requestReader.taskId()).thenReturn(9938L);
        when(requestReader.getPayload()).thenReturn(new UnsafeBuffer(PAYLOAD));

        when(taskInstanceIndex.get(eq(9938L), anyLong())).thenReturn(9876L);

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        final StubLogReader logReader = new StubLogReader(9876L, taskContext.getLog())
                .addEntry(createTaskInstanceWriter(9938L, 765L, TaskInstanceState.LOCKED));

        handler.requestReader = requestReader;
        handler.logReader = logReader;

        // when
        handler.onRequest(taskContext, message, 123, 456, response);

        // then
        final InOrder inOrder = inOrder(response, logWriter);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(SingleTaskAckResponseReader.class)
                    .matching((t) -> t.taskId(), 9938L)));

        inOrder.verify(logWriter).write(argThat(
                BufferWriterMatcher.writesProperties(TaskInstanceReader.class)
                    .matching((t) -> t.getPayload(), hasBytes(PAYLOAD))
                    .matching((t) -> t.getTaskType(), hasBytes(TASK_TYPE))
                    .matching((t) -> t.id(), 9938L)
                    .matching((t) -> Integer.toUnsignedLong((int) t.lockOwnerId()),
                            TaskInstanceEncoder.lockOwnerIdNullValue()) // https://github.com/camunda-tngp/tasks/issues/16
                    .matching((t) -> t.lockTime(), TaskInstanceEncoder.lockTimeNullValue())
                    .matching((t) -> t.prevVersionPosition(), 9876L)
                    .matching((t) -> t.resourceId(), 0)
                    .matching((t) -> t.shardId(), 0)
                    .matching((t) -> t.state(), TaskInstanceState.COMPLETED)
                    .matching((t) -> t.taskTypeHash(), (long) TASK_TYPE_HASH)
                    .matching((t) -> t.wfActivityInstanceEventKey(), 123L)
                    .matching((t) -> t.wfRuntimeResourceId(), 789)));

        inOrder.verify(response).defer(anyLong(), eq(handler));

        verifyNoMoreInteractions(response, logWriter);
    }

    @Test
    public void shouldWriteErrorResponseOnMissingTaskId()
    {
        // given
        final CompleteTaskHandler handler = new CompleteTaskHandler();
        final LogWriter logWriter = taskContext.getLogWriter();

        when(requestReader.consumerId()).thenReturn(765);
        when(requestReader.taskId()).thenReturn(CompleteTaskEncoder.taskIdNullValue());
        when(requestReader.getPayload()).thenReturn(new UnsafeBuffer(PAYLOAD));

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        handler.requestReader = requestReader;

        // when
        handler.onRequest(taskContext, message, 123, 456, response);

        // then
        final InOrder inOrder = inOrder(response, logWriter);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(ErrorReader.class)
                    .matching((e) -> e.componentCode(), TaskErrors.COMPONENT_CODE)
                    .matching((e) -> e.detailCode(), TaskErrors.COMPLETE_TASK_ERROR)
                    .matching((e) -> e.errorMessage(), "Task id is required")));

        inOrder.verify(response).commit();

        verifyNoMoreInteractions(response);
        verifyZeroInteractions(logWriter);
    }

    @Test
    public void shouldWriteErrorResponseOnMissingConsumerId()
    {
        // given
        final CompleteTaskHandler handler = new CompleteTaskHandler();
        final LogWriter logWriter = taskContext.getLogWriter();

        when(requestReader.consumerId()).thenReturn(CompleteTaskEncoder.consumerIdNullValue());
        when(requestReader.taskId()).thenReturn(123L);
        when(requestReader.getPayload()).thenReturn(new UnsafeBuffer(PAYLOAD));

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        handler.requestReader = requestReader;

        // when
        handler.onRequest(taskContext, message, 123, 456, response);

        // then
        final InOrder inOrder = inOrder(response, logWriter);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(ErrorReader.class)
                    .matching((e) -> e.componentCode(), TaskErrors.COMPONENT_CODE)
                    .matching((e) -> e.detailCode(), TaskErrors.COMPLETE_TASK_ERROR)
                    .matching((e) -> e.errorMessage(), "Consumer id is required")));

        inOrder.verify(response).commit();

        verifyNoMoreInteractions(response);
        verifyZeroInteractions(logWriter);
    }

    @Test
    public void shouldWriteErrorResponseForNonExistingTask()
    {
        // given
        final CompleteTaskHandler handler = new CompleteTaskHandler();
        final LogWriter logWriter = taskContext.getLogWriter();

        when(requestReader.consumerId()).thenReturn(123);
        when(requestReader.taskId()).thenReturn(9938L);
        when(requestReader.getPayload()).thenReturn(new UnsafeBuffer(PAYLOAD));

        when(taskInstanceIndex.get(eq(9938L), anyLong())).thenReturn(-1L);

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        handler.requestReader = requestReader;

        // when
        handler.onRequest(taskContext, message, 123, 456, response);

        // then
        final InOrder inOrder = inOrder(response, logWriter);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(ErrorReader.class)
                    .matching((e) -> e.componentCode(), TaskErrors.COMPONENT_CODE)
                    .matching((e) -> e.detailCode(), TaskErrors.COMPLETE_TASK_ERROR)
                    .matching((e) -> e.errorMessage(), "Task does not exist or is not locked")));

        inOrder.verify(response).commit();

        verifyNoMoreInteractions(response);
        verifyZeroInteractions(logWriter);
    }

    @Test
    public void shouldWriteErrorResponseForWrongConsumerId()
    {
        // given
        final CompleteTaskHandler handler = new CompleteTaskHandler();
        final LogWriter logWriter = taskContext.getLogWriter();

        when(requestReader.consumerId()).thenReturn(765);
        when(requestReader.taskId()).thenReturn(9938L);
        when(requestReader.getPayload()).thenReturn(new UnsafeBuffer(PAYLOAD));

        when(taskInstanceIndex.get(eq(9938L), anyLong())).thenReturn(9876L);

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        final StubLogReader logReader = new StubLogReader(9876L, taskContext.getLog())
                .addEntry(createTaskInstanceWriter(9938L, 766L, TaskInstanceState.LOCKED));

        handler.requestReader = requestReader;
        handler.logReader = logReader;

        // when
        handler.onRequest(taskContext, message, 123, 456, response);

        // then
        final InOrder inOrder = inOrder(response, logWriter);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(ErrorReader.class)
                .matching((e) -> e.componentCode(), TaskErrors.COMPONENT_CODE)
                .matching((e) -> e.detailCode(), TaskErrors.COMPLETE_TASK_ERROR)
                .matching((e) -> e.errorMessage(), "Task is currently not locked by the provided consumer")));

        inOrder.verify(response).commit();

        verifyNoMoreInteractions(response);
        verifyZeroInteractions(logWriter);
    }

    protected TaskInstanceWriter createTaskInstanceWriter(long taskId, long lockOwner, TaskInstanceState state)
    {
        final TaskInstanceWriter writer = new TaskInstanceWriter();

        writer.lockOwner(lockOwner);
        writer.lockTime(TaskInstanceDecoder.lockTimeMaxValue());
        writer.id(taskId);
        writer.state(state);
        writer.prevVersionPosition(TaskInstanceDecoder.prevVersionPositionNullValue());
        writer.wfActivityInstanceEventKey(123L);
        writer.wfRuntimeResourceId(789);
        writer.payload(new UnsafeBuffer(PAYLOAD), 0, PAYLOAD.length);
        writer.taskType(new UnsafeBuffer(TASK_TYPE), 0, TASK_TYPE.length);

        return writer;
    }
}

package org.camunda.tngp.broker.taskqueue.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferMatcher.hasBytes;
import static org.hamcrest.CoreMatchers.anything;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
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
import org.camunda.tngp.broker.taskqueue.MockTaskQueueContext;
import org.camunda.tngp.broker.taskqueue.PollAndLockTaskRequestReader;
import org.camunda.tngp.broker.taskqueue.TaskErrors;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.protocol.error.ErrorReader;
import org.camunda.tngp.protocol.taskqueue.LockTaskBatchResponseReader;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchEncoder;
import org.camunda.tngp.protocol.wf.Constants;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class LockTaskBatchHandlerTest
{

    @Mock
    protected DirectBuffer requestBuffer;

    @Mock
    protected DeferredResponse response;

    @Mock
    protected PollAndLockTaskRequestReader requestReader;

    @Mock
    protected TaskInstanceReader lockableTaskReader;

    @Mock
    protected LockableTaskFinder lockableTaskFinder;

    @Mock
    protected Bytes2LongHashIndex taskTypePositionIndex;

    protected static final byte[] TASK_TYPE = "ladida".getBytes(StandardCharsets.UTF_8);
    protected static final int TASK_TYPE_HASH = TaskTypeHash.hashCode(TASK_TYPE, TASK_TYPE.length);

    protected static final byte[] PAYLOAD = "maven".getBytes(StandardCharsets.UTF_8);

    protected TaskQueueContext taskContext;
    protected LogWriter logWriter;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        taskContext = new MockTaskQueueContext();
        logWriter = taskContext.getLogWriter();

        final HashIndexManager<Bytes2LongHashIndex> indexManager = mock(HashIndexManager.class);

        when(indexManager.getIndex()).thenReturn(taskTypePositionIndex);

        taskContext.setTaskTypePositionIndex(indexManager);
    }

    protected void mockTaskInstance(TaskInstanceReader taskReaderMock)
    {
        when(taskReaderMock.lockOwnerId()).thenReturn(TaskInstanceDecoder.lockOwnerIdNullValue());
        when(taskReaderMock.lockTime()).thenReturn(TaskInstanceDecoder.lockTimeNullValue());
        when(taskReaderMock.id()).thenReturn(642L);
        when(taskReaderMock.state()).thenReturn(TaskInstanceState.NEW);
        when(taskReaderMock.prevVersionPosition()).thenReturn(TaskInstanceDecoder.prevVersionPositionNullValue());
        when(taskReaderMock.taskTypeHash()).thenReturn((long) TASK_TYPE_HASH);
        when(taskReaderMock.resourceId()).thenReturn(0);
        when(taskReaderMock.shardId()).thenReturn(0);
        when(taskReaderMock.wfActivityInstanceEventKey()).thenReturn(123L);
        when(taskReaderMock.wfRuntimeResourceId()).thenReturn(789);
        when(taskReaderMock.getPayload()).thenReturn(new UnsafeBuffer(PAYLOAD));
        when(taskReaderMock.getTaskType()).thenReturn(new UnsafeBuffer(TASK_TYPE));
    }

    @Test
    public void shouldLockTasks()
    {
        // given
        final LockTaskBatchHandler handler = new LockTaskBatchHandler();
        when(requestReader.consumerId()).thenReturn(123);
        when(requestReader.lockTime()).thenReturn(1234L);
        when(requestReader.maxTasks()).thenReturn(5);
        when(requestReader.taskType()).thenReturn(new UnsafeBuffer(TASK_TYPE));

        mockTaskInstance(lockableTaskReader);

        when(taskTypePositionIndex.get(argThat(hasBytes(TASK_TYPE)), anyInt(), anyInt(), anyLong())).thenReturn(55L);

        when(lockableTaskFinder.findNextLockableTask()).thenReturn(true, false);
        when(lockableTaskFinder.getLockableTask()).thenReturn(lockableTaskReader);
        when(lockableTaskFinder.getLockableTaskPosition()).thenReturn(876L);

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        handler.lockableTaskFinder = lockableTaskFinder;
        handler.taskInstanceReader = lockableTaskReader;
        handler.requestReader = requestReader;

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response, lockableTaskFinder, logWriter, lockableTaskReader);

        inOrder.verify(lockableTaskFinder).init(eq(taskContext.getLog()), eq(55L), eq(TASK_TYPE_HASH), argThat(hasBytes(TASK_TYPE)));
        inOrder.verify(lockableTaskFinder).findNextLockableTask();
        inOrder.verify(lockableTaskFinder).getLockableTask();
        inOrder.verify(lockableTaskFinder).getLockableTaskPosition();

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(LockTaskBatchResponseReader.class)
                    .matching((r) -> r.consumerId(), 123)
                    .matching((r) -> r.lockTime(), anything()) // TODO: something like clockutil?
                    .matching((r) -> r.numTasks(), 1)
                    .matching((r) -> r.nextTask().currentTaskId(), 642L)
                    .matching((r) -> r.currentTaskPayload(), hasBytes(PAYLOAD))));

        inOrder.verify(logWriter).write(argThat(
                BufferWriterMatcher.writesProperties(TaskInstanceReader.class)
                    .matching((t) -> t.id(), 642L)
                    .matching((t) -> t.lockOwnerId(), 123L)
                    .matching((t) -> t.lockTime(), anything())
                    .matching((t) -> t.prevVersionPosition(), 876L)
                    .matching((t) -> t.resourceId(), 0)
                    .matching((t) -> t.shardId(), 0)
                    .matching((t) -> t.state(), TaskInstanceState.LOCKED)
                    .matching((t) -> t.taskTypeHash(), (long) TASK_TYPE_HASH)
                    .matching((t) -> t.wfActivityInstanceEventKey(), 123L)
                    .matching((t) -> t.wfRuntimeResourceId(), 789)
                    .matching((t) -> t.getPayload(), hasBytes(PAYLOAD))
                    .matching((t) -> t.getTaskType(), hasBytes(TASK_TYPE))
                ));
        inOrder.verify(response).defer(anyLong(), eq(handler));

        verifyNoMoreInteractions(response, lockableTaskFinder, logWriter);
    }

    @Test
    public void shouldNotLockTasksIfNoneAvailable()
    {
        // given
        final LockTaskBatchHandler handler = new LockTaskBatchHandler();
        when(requestReader.consumerId()).thenReturn(123);
        when(requestReader.lockTime()).thenReturn(1234L);
        when(requestReader.maxTasks()).thenReturn(5);
        when(requestReader.taskType()).thenReturn(new UnsafeBuffer(TASK_TYPE));

        when(lockableTaskFinder.findNextLockableTask()).thenReturn(false);
        when(lockableTaskFinder.getLockableTask()).thenReturn(null);
        when(lockableTaskFinder.getLockableTaskPosition()).thenReturn(-1L);

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        when(taskTypePositionIndex.get(argThat(hasBytes(TASK_TYPE)), anyInt(), anyInt(), anyLong())).thenReturn(55L);

        handler.lockableTaskFinder = lockableTaskFinder;
        handler.requestReader = requestReader;

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response, lockableTaskFinder, logWriter);

        inOrder.verify(lockableTaskFinder).init(eq(taskContext.getLog()), eq(55L), eq(TASK_TYPE_HASH), argThat(hasBytes(TASK_TYPE)));
        inOrder.verify(lockableTaskFinder).findNextLockableTask();

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(LockTaskBatchResponseReader.class)
                    .matching((r) -> r.consumerId(), 123)
                    .matching((r) -> r.lockTime(), anything()) // TODO: something like clockutil?
                    .matching((r) -> r.numTasks(), 0)));

        inOrder.verify(response).commit();

        verifyNoMoreInteractions(lockableTaskFinder, response);
        verifyZeroInteractions(logWriter);
    }

    @Test
    public void shouldWriteErrorResponseOnOverlongTaskType()
    {
        // given
        final LockTaskBatchHandler handler = new LockTaskBatchHandler();
        when(requestReader.consumerId()).thenReturn(123);
        when(requestReader.lockTime()).thenReturn(1234L);
        when(requestReader.maxTasks()).thenReturn(5);
        when(requestReader.taskType()).thenReturn(new UnsafeBuffer(new byte[Constants.WF_TYPE_KEY_MAX_LENGTH + 1]));

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        handler.lockableTaskFinder = lockableTaskFinder;
        handler.taskInstanceReader = lockableTaskReader;
        handler.requestReader = requestReader;

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response, lockableTaskFinder, logWriter);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(ErrorReader.class)
                    .matching((e) -> e.componentCode(), TaskErrors.COMPONENT_CODE)
                    .matching((e) -> e.detailCode(), TaskErrors.LOCK_TASKS_ERROR)
                    .matching((e) -> e.errorMessage(), "Task type is too long")));

        inOrder.verify(response).commit();

        verifyNoMoreInteractions(response);
        verifyZeroInteractions(lockableTaskFinder, logWriter);
    }

    @Test
    public void shouldWriteErrorResponseOnMissingConsumerId()
    {
        // given
        final LockTaskBatchHandler handler = new LockTaskBatchHandler();
        when(requestReader.consumerId()).thenReturn(LockedTaskBatchEncoder.consumerIdNullValue());
        when(requestReader.lockTime()).thenReturn(1234L);
        when(requestReader.maxTasks()).thenReturn(5);
        when(requestReader.taskType()).thenReturn(new UnsafeBuffer(new byte[Constants.WF_TYPE_KEY_MAX_LENGTH + 1]));

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        handler.lockableTaskFinder = lockableTaskFinder;
        handler.taskInstanceReader = lockableTaskReader;
        handler.requestReader = requestReader;

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response, lockableTaskFinder, logWriter);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(ErrorReader.class)
                    .matching((e) -> e.componentCode(), TaskErrors.COMPONENT_CODE)
                    .matching((e) -> e.detailCode(), TaskErrors.LOCK_TASKS_ERROR)
                    .matching((e) -> e.errorMessage(), "Consumer id is required")));

        inOrder.verify(response).commit();

        verifyNoMoreInteractions(response);
        verifyZeroInteractions(lockableTaskFinder, logWriter);
    }

    @Test
    public void shouldWriteErrorResponseOnMissingLockTimeout()
    {
        // given
        final LockTaskBatchHandler handler = new LockTaskBatchHandler();
        when(requestReader.consumerId()).thenReturn(123);
        when(requestReader.lockTime()).thenReturn(LockedTaskBatchEncoder.lockTimeNullValue());
        when(requestReader.maxTasks()).thenReturn(5);
        when(requestReader.taskType()).thenReturn(new UnsafeBuffer(new byte[Constants.WF_TYPE_KEY_MAX_LENGTH + 1]));

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        handler.lockableTaskFinder = lockableTaskFinder;
        handler.taskInstanceReader = lockableTaskReader;
        handler.requestReader = requestReader;

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response, lockableTaskFinder, logWriter);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(ErrorReader.class)
                    .matching((e) -> e.componentCode(), TaskErrors.COMPONENT_CODE)
                    .matching((e) -> e.detailCode(), TaskErrors.LOCK_TASKS_ERROR)
                    .matching((e) -> e.errorMessage(), "Lock time is required")));

        inOrder.verify(response).commit();

        verifyNoMoreInteractions(response);
        verifyZeroInteractions(lockableTaskFinder, logWriter);
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

        handler.lockableTaskFinder = lockableTaskFinder;
        handler.taskInstanceReader = lockableTaskReader;
        handler.requestReader = requestReader;

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response, lockableTaskFinder, logWriter);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(ErrorReader.class)
                    .matching((e) -> e.componentCode(), TaskErrors.COMPONENT_CODE)
                    .matching((e) -> e.detailCode(), TaskErrors.LOCK_TASKS_ERROR)
                    .matching((e) -> e.errorMessage(), "Task type is required")));

        inOrder.verify(response).commit();

        verifyNoMoreInteractions(response);
        verifyZeroInteractions(lockableTaskFinder, logWriter);
    }
}

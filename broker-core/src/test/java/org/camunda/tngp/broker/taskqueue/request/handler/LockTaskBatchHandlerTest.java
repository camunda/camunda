package org.camunda.tngp.broker.taskqueue.request.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;
import static org.camunda.tngp.broker.test.util.BufferMatcher.hasBytes;
import static org.hamcrest.CoreMatchers.anything;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.taskqueue.MockTaskQueueContext;
import org.camunda.tngp.broker.taskqueue.PollAndLockTaskRequestReader;
import org.camunda.tngp.broker.taskqueue.TaskErrors;
import org.camunda.tngp.broker.taskqueue.TaskInstanceReader;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.test.util.BufferWriterMatcher;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
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

    protected LockableTaskFinder lockableTaskFinder;

    protected StubLogReader logReader;

    @Mock
    protected Bytes2LongHashIndex taskTypePositionIndex;

    protected static final byte[] TASK_TYPE = "ladida".getBytes(StandardCharsets.UTF_8);
    protected static final int TASK_TYPE_HASH = TaskTypeHash.hashCode(TASK_TYPE, TASK_TYPE.length);

    protected static final byte[] PAYLOAD = "maven".getBytes(StandardCharsets.UTF_8);

    protected TaskQueueContext taskContext;
    protected StubLogWriter logWriter;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        taskContext = new MockTaskQueueContext();
        logWriter = new StubLogWriter();
        taskContext.setLogWriter(logWriter);
        logReader = new StubLogReader(55L, taskContext.getLog());
        lockableTaskFinder = new LockableTaskFinder(logReader);

        final HashIndexManager<Bytes2LongHashIndex> indexManager = mock(HashIndexManager.class);

        when(indexManager.getIndex()).thenReturn(taskTypePositionIndex);

        taskContext.setTaskTypePositionIndex(indexManager);
    }

    protected void writeTaskInstance(TaskInstanceWriter taskWriter)
    {
        taskWriter.lockOwner(TaskInstanceDecoder.lockOwnerIdNullValue());
        taskWriter.lockTime(TaskInstanceDecoder.lockTimeNullValue());
        taskWriter.id(642L);
        taskWriter.state(TaskInstanceState.NEW);
        taskWriter.prevVersionPosition(TaskInstanceDecoder.prevVersionPositionNullValue());
        taskWriter.wfActivityInstanceEventKey(123L);
        taskWriter.wfRuntimeResourceId(789);
        taskWriter.payload(new UnsafeBuffer(PAYLOAD), 0, PAYLOAD.length);
        taskWriter.taskType(new UnsafeBuffer(TASK_TYPE), 0, TASK_TYPE.length);
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

        final TaskInstanceWriter writer = new TaskInstanceWriter();
        writeTaskInstance(writer);
        logReader.addEntry(writer);

        when(taskTypePositionIndex.get(argThat(hasBytes(TASK_TYPE)), anyInt(), anyInt(), anyLong())).thenReturn(55L);

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        handler.lockableTaskFinder = lockableTaskFinder;
        handler.taskInstanceReader = lockableTaskReader;
        handler.requestReader = requestReader;

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        assertThat(logWriter.size()).isEqualTo(1);
        final TaskInstanceReader taskInstanceReader = logWriter.getEntryAs(0, TaskInstanceReader.class);

        assertThat(taskInstanceReader.id()).isEqualTo(642L);
        assertThat(taskInstanceReader.lockOwnerId()).isEqualTo(123L);
        assertThat(taskInstanceReader.lockTime()).isGreaterThan(0L); // TODO: assert time (clockutil?)
        assertThat(taskInstanceReader.prevVersionPosition()).isEqualTo(55L);
        assertThat(taskInstanceReader.resourceId()).isEqualTo(0);
        assertThat(taskInstanceReader.shardId()).isEqualTo(0);
        assertThat(taskInstanceReader.state()).isEqualTo(TaskInstanceState.LOCKED);
        assertThat(taskInstanceReader.taskTypeHash()).isEqualTo((long) TASK_TYPE_HASH);
        assertThat(taskInstanceReader.wfActivityInstanceEventKey()).isEqualTo(123L);
        assertThat(taskInstanceReader.wfRuntimeResourceId()).isEqualTo(789);
        assertThatBuffer(taskInstanceReader.getPayload()).hasBytes(PAYLOAD);
        assertThatBuffer(taskInstanceReader.getTaskType()).hasBytes(TASK_TYPE);

        verify(response).defer();

        verifyNoMoreInteractions(response);
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

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        when(taskTypePositionIndex.get(argThat(hasBytes(TASK_TYPE)), anyInt(), anyInt(), anyLong())).thenReturn(55L);

        handler.lockableTaskFinder = lockableTaskFinder;
        handler.requestReader = requestReader;

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(LockTaskBatchResponseReader.class)
                    .matching((r) -> r.consumerId(), 123)
                    .matching((r) -> r.lockTime(), anything()) // TODO: something like clockutil?
                    .matching((r) -> r.numTasks(), 0)));

        inOrder.verify(response).commit();

        verifyNoMoreInteractions(response);
        assertThat(logWriter.size()).isEqualTo(0);
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

        handler.lockableTaskFinder = lockableTaskFinder;
        handler.taskInstanceReader = lockableTaskReader;
        handler.requestReader = requestReader;

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(ErrorReader.class)
                    .matching((e) -> e.componentCode(), TaskErrors.COMPONENT_CODE)
                    .matching((e) -> e.detailCode(), TaskErrors.LOCK_TASKS_ERROR)
                    .matching((e) -> e.errorMessage(), "Task type is too long")));

        inOrder.verify(response).commit();

        verifyNoMoreInteractions(response);
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

        handler.lockableTaskFinder = lockableTaskFinder;
        handler.taskInstanceReader = lockableTaskReader;
        handler.requestReader = requestReader;

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(ErrorReader.class)
                    .matching((e) -> e.componentCode(), TaskErrors.COMPONENT_CODE)
                    .matching((e) -> e.detailCode(), TaskErrors.LOCK_TASKS_ERROR)
                    .matching((e) -> e.errorMessage(), "Consumer id is required")));

        inOrder.verify(response).commit();

        verifyNoMoreInteractions(response);
        assertThat(logWriter.size()).isEqualTo(0);
    }

    @Test
    public void shouldWriteErrorResponseOnMissingLockTimeout()
    {
        // given
        final LockTaskBatchHandler handler = new LockTaskBatchHandler();
        when(requestReader.consumerId()).thenReturn(123);
        when(requestReader.lockTime()).thenReturn(LockedTaskBatchEncoder.lockTimeNullValue());
        when(requestReader.maxTasks()).thenReturn(5);
        when(requestReader.taskType()).thenReturn(new UnsafeBuffer(new byte[Constants.WF_DEF_KEY_MAX_LENGTH + 1]));

        when(response.allocateAndWrite(any())).thenReturn(true, false);

        handler.lockableTaskFinder = lockableTaskFinder;
        handler.taskInstanceReader = lockableTaskReader;
        handler.requestReader = requestReader;

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(ErrorReader.class)
                    .matching((e) -> e.componentCode(), TaskErrors.COMPONENT_CODE)
                    .matching((e) -> e.detailCode(), TaskErrors.LOCK_TASKS_ERROR)
                    .matching((e) -> e.errorMessage(), "Lock time is required")));

        inOrder.verify(response).commit();

        verifyNoMoreInteractions(response);
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

        handler.lockableTaskFinder = lockableTaskFinder;
        handler.taskInstanceReader = lockableTaskReader;
        handler.requestReader = requestReader;

        // when
        final long returnValue = handler.onRequest(taskContext, requestBuffer, 123, 456, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(ErrorReader.class)
                    .matching((e) -> e.componentCode(), TaskErrors.COMPONENT_CODE)
                    .matching((e) -> e.detailCode(), TaskErrors.LOCK_TASKS_ERROR)
                    .matching((e) -> e.errorMessage(), "Task type is required")));

        inOrder.verify(response).commit();

        verifyNoMoreInteractions(response);
        assertThat(logWriter.size()).isEqualTo(0);
    }


}

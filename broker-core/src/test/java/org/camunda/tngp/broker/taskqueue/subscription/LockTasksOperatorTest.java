package org.camunda.tngp.broker.taskqueue.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;
import static org.camunda.tngp.broker.test.util.BufferMatcher.hasBytes;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.request.handler.TaskTypeHash;
import org.camunda.tngp.broker.test.util.BufferWriterUtil;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.protocol.log.TaskInstanceDecoder;
import org.camunda.tngp.protocol.log.TaskInstanceState;
import org.camunda.tngp.protocol.taskqueue.LockTaskBatchResponseReader;
import org.camunda.tngp.protocol.taskqueue.SubscribedTaskReader;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;
import org.camunda.tngp.transport.singlemessage.DataFramePool;
import org.camunda.tngp.transport.singlemessage.OutgoingDataFrame;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LockTasksOperatorTest
{

    public static final byte[] TASK_TYPE1 = "foo".getBytes(StandardCharsets.UTF_8);
    public static final byte[] TASK_TYPE2 = "bar".getBytes(StandardCharsets.UTF_8);
    public static final int TASK_TYPE1_HASH = TaskTypeHash.hashCode(TASK_TYPE1, TASK_TYPE1.length);

    protected static final DirectBuffer TASK_TYPE1_BUF = new UnsafeBuffer(TASK_TYPE1);
    protected static final DirectBuffer TASK_TYPE2_BUF = new UnsafeBuffer(TASK_TYPE2);

    public static final byte[] PAYLOAD = "$$$".getBytes(StandardCharsets.UTF_8);

    @Mock
    protected DataFramePool dataFramePool;
    @Mock
    protected OutgoingDataFrame dataFrame;

    @Mock
    protected DeferredResponsePool deferredResponsePool;
    @Mock
    protected DeferredResponse deferredResponse;

    @Mock
    protected Bytes2LongHashIndex taskTypeIndex;

    @Captor
    protected ArgumentCaptor<BufferWriter> captor;

    protected StubLogReader logReader;
    protected StubLogWriter logWriter;


    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        logWriter = new StubLogWriter();
        logReader = new StubLogReader(null, 300);
    }

    @Test
    public void shouldLockTasksForSubscription()
    {
        // given
        final TaskInstanceWriter task = taskWriter(13L, TaskInstanceState.NEW, TASK_TYPE1);
        task.lockOwner(TaskInstanceDecoder.lockOwnerIdNullValue());
        task.lockTime(TaskInstanceDecoder.lockTimeNullValue());
        task.prevVersionPosition(TaskInstanceDecoder.prevVersionPositionNullValue());
        task.wfActivityInstanceEventKey(123L);
        task.wfRuntimeResourceId(789);
        task.wfInstanceId(123123L);
        task.payload(new UnsafeBuffer(PAYLOAD), 0, PAYLOAD.length);

        logReader.addEntry(task);

        when(taskTypeIndex.get(argThat(hasBytes(TASK_TYPE1)), eq(0), eq(TASK_TYPE1.length), anyLong()))
            .thenReturn(logReader.getEntryPosition(0));

        final LockTasksOperator operator = new LockTasksOperator(taskTypeIndex, logReader, logWriter, dataFramePool, 1);

        operator.openSubscription(13, 32, 123123L, 43, TASK_TYPE1_BUF);

        // when
        operator.lockTasks();

        // then
        assertThat(logWriter.size()).isEqualTo(1);

        final TaskInstanceReader taskInstanceReader = logWriter.getEntryAs(0, TaskInstanceReader.class);

        assertThat(taskInstanceReader.id()).isEqualTo(13L);
        assertThat(taskInstanceReader.lockOwnerId()).isEqualTo(32);
        assertThat(taskInstanceReader.lockTime()).isGreaterThan(0L); // TODO: assert time (clockutil?)
        assertThat(taskInstanceReader.prevVersionPosition()).isEqualTo(logReader.getEntryPosition(0));
        assertThat(taskInstanceReader.resourceId()).isEqualTo(0);
        assertThat(taskInstanceReader.shardId()).isEqualTo(0);
        assertThat(taskInstanceReader.state()).isEqualTo(TaskInstanceState.LOCKED);
        assertThat(taskInstanceReader.taskTypeHash()).isEqualTo(Integer.toUnsignedLong(TASK_TYPE1_HASH));
        assertThat(taskInstanceReader.wfActivityInstanceEventKey()).isEqualTo(123L);
        assertThat(taskInstanceReader.wfRuntimeResourceId()).isEqualTo(789);
        assertThat(taskInstanceReader.wfInstanceId()).isEqualTo(123123L);
        assertThatBuffer(taskInstanceReader.getPayload()).hasBytes(PAYLOAD);
        assertThatBuffer(taskInstanceReader.getTaskType()).hasBytes(TASK_TYPE1);
    }

    @Test
    public void shouldLockMultipleTasksPerCycle()
    {
        // given
        logReader.addEntry(taskWriter(13L, TaskInstanceState.NEW, TASK_TYPE1));
        logReader.addEntry(taskWriter(14L, TaskInstanceState.NEW, TASK_TYPE1));

        when(taskTypeIndex.get(argThat(hasBytes(TASK_TYPE1)), eq(0), eq(TASK_TYPE1.length), anyLong()))
            .thenReturn(logReader.getEntryPosition(0));

        final LockTasksOperator operator = new LockTasksOperator(taskTypeIndex, logReader, logWriter, dataFramePool, 1);
        operator.openSubscription(13, 32, 123123L, 43, TASK_TYPE1_BUF);

        // when
        operator.lockTasks();

        // then
        assertThat(logWriter.size()).isEqualTo(2);

        final TaskInstanceReader task1 = logWriter.getEntryAs(0, TaskInstanceReader.class);
        assertThat(task1.id()).isEqualTo(13L);

        final TaskInstanceReader task2 = logWriter.getEntryAs(0, TaskInstanceReader.class);
        assertThat(task2.id()).isEqualTo(13L);
    }

    @Test
    public void shouldLockMultipleTasksOfDifferentTypesInterleaved()
    {
        // given
        logReader.addEntry(taskWriter(13L, TaskInstanceState.NEW, TASK_TYPE1));
        logReader.addEntry(taskWriter(14L, TaskInstanceState.NEW, TASK_TYPE2));
        logReader.addEntry(taskWriter(14L, TaskInstanceState.LOCKED, TASK_TYPE2));

        when(taskTypeIndex.get(argThat(hasBytes(TASK_TYPE1)), eq(0), eq(TASK_TYPE1.length), anyLong()))
            .thenReturn(logReader.getEntryPosition(0));
        when(taskTypeIndex.get(argThat(hasBytes(TASK_TYPE2)), eq(0), eq(TASK_TYPE1.length), anyLong()))
            .thenReturn(logReader.getEntryPosition(2));

        final LockTasksOperator operator = new LockTasksOperator(taskTypeIndex, logReader, logWriter, dataFramePool, 1);
        operator.openSubscription(13, 32, 123123L, 43, TASK_TYPE1_BUF);
        operator.openSubscription(13, 32, 123123L, 43, TASK_TYPE2_BUF);

        // when
        operator.lockTasks();

        // then only the first task has been locked
        assertThat(logWriter.size()).isEqualTo(1);

        final TaskInstanceReader task = logWriter.getEntryAs(0, TaskInstanceReader.class);
        assertThat(task.id()).isEqualTo(13L);
    }


    @Test
    public void shouldLockTasksForAdhocSubscription()
    {
        // given
        logReader.addEntry(taskWriter(13L, TaskInstanceState.NEW, TASK_TYPE1));

        when(taskTypeIndex.get(argThat(hasBytes(TASK_TYPE1)), eq(0), eq(TASK_TYPE1.length), anyLong()))
            .thenReturn(logReader.getEntryPosition(0));

        final LockTasksOperator operator = new LockTasksOperator(taskTypeIndex, logReader, logWriter, dataFramePool, 1);

        operator.openAdhocSubscription(deferredResponse, 32, 123123L, 43, TASK_TYPE1_BUF);

        // when
        operator.lockTasks();

        // then
        assertThat(logWriter.size()).isEqualTo(1);

        final TaskInstanceReader taskInstanceReader = logWriter.getEntryAs(0, TaskInstanceReader.class);

        assertThat(taskInstanceReader.id()).isEqualTo(13L);
        assertThat(taskInstanceReader.state()).isEqualTo(TaskInstanceState.LOCKED);
    }

    @Test
    public void shouldPushLockedTaskForSubscription()
    {
        // given
        logReader.addEntry(taskWriter(123L, TaskInstanceState.NEW, TASK_TYPE1));

        when(taskTypeIndex.get(argThat(hasBytes(TASK_TYPE1)), eq(0), eq(TASK_TYPE1.length), anyLong()))
            .thenReturn(logReader.getEntryPosition(0));

        final LockTasksOperator operator = new LockTasksOperator(taskTypeIndex, logReader, logWriter, dataFramePool, 1);

        final TaskSubscription subscription = operator.openSubscription(13, 32, 123123L, 43, TASK_TYPE1_BUF);

        when(dataFramePool.openFrame(anyInt(), anyInt())).thenReturn(dataFrame, (OutgoingDataFrame) null);

        operator.lockTasks();

        // when
        operator.onTaskLocked(taskReader(123L, 456L, 789L));

        // then
        verify(dataFramePool).openFrame(anyInt(), eq(13));

        final InOrder inOrder = inOrder(dataFrame);
        inOrder.verify(dataFrame).write(captor.capture());

        final SubscribedTaskReader reader = new SubscribedTaskReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);

        assertThat(reader.subscriptionId()).isEqualTo(subscription.getId());
        assertThat(reader.taskId()).isEqualTo(123L);
        assertThat(reader.lockTime()).isEqualTo(456L);
        assertThat(reader.wfInstanceId()).isEqualTo(789L);

        inOrder.verify(dataFrame).commit();
    }

    @Test
    public void shouldReturnLockedTaskForAdhocSubscription()
    {
        // given
        logReader.addEntry(taskWriter(123L, TaskInstanceState.NEW, TASK_TYPE1));

        when(taskTypeIndex.get(argThat(hasBytes(TASK_TYPE1)), eq(0), eq(TASK_TYPE1.length), anyLong()))
            .thenReturn(logReader.getEntryPosition(0));

        final LockTasksOperator operator = new LockTasksOperator(taskTypeIndex, logReader, logWriter, dataFramePool, 1);
        operator.openAdhocSubscription(deferredResponse, 32, 123123L, 43, TASK_TYPE1_BUF);

        operator.lockTasks();

        // when
        operator.onTaskLocked(taskReader(123L, 456L, 789L));

        // then
        final InOrder inOrder = inOrder(deferredResponse);

        inOrder.verify(deferredResponse).allocateAndWrite(captor.capture());

        final LockTaskBatchResponseReader reader = new LockTaskBatchResponseReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);
        assertThat(reader.consumerId()).isEqualTo(32);
        assertThat(reader.numTasks()).isEqualTo(1);
        assertThat(reader.nextTask().currentTaskId()).isEqualTo(123L);
        assertThat(reader.currentTaskLockTime()).isEqualTo(456L);
        assertThat(reader.currentTaskWfInstanceId()).isEqualTo(789L);

        inOrder.verify(deferredResponse).commit();

    }

    @Test
    public void shouldIgnoreTaskThatIsNotPending()
    {
        // given
        final LockTasksOperator operator = new LockTasksOperator(taskTypeIndex, logReader, logWriter, dataFramePool, 1);

        // when
        operator.onTaskLocked(taskReader(123L, 456L, 789L));

        // then
        verifyZeroInteractions(deferredResponsePool, dataFramePool);
    }

    @Test
    public void shouldRemoveSubscription()
    {
        // given
        final TaskInstanceWriter task = taskWriter(13L, TaskInstanceState.NEW, TASK_TYPE1);
        logReader.addEntry(task);

        when(taskTypeIndex.get(argThat(hasBytes(TASK_TYPE1)), eq(0), eq(TASK_TYPE1.length), anyLong()))
            .thenReturn(logReader.getEntryPosition(0));

        final LockTasksOperator operator = new LockTasksOperator(taskTypeIndex, logReader, logWriter, dataFramePool, 1);
        final TaskSubscription subscription = operator.openSubscription(13, 32, 123123L, 43, TASK_TYPE1_BUF);

        // when
        operator.removeSubscription(subscription);

        // then
        final int lockedTasks = operator.lockTasks();
        assertThat(lockedTasks).isEqualTo(0);

        assertThat(logWriter.size()).isEqualTo(0);
    }

    @Test
    public void shouldRemoveSubscriptionsOnChannelClose()
    {
        // given
        final LockTasksOperator operator = new LockTasksOperator(taskTypeIndex, logReader, logWriter, dataFramePool, 1);
        final TaskSubscription sub1 = operator.openSubscription(13, 32, 123123L, 43, TASK_TYPE1_BUF);
        final TaskSubscription sub2 = operator.openSubscription(14, 32, 123123L, 43, TASK_TYPE1_BUF);
        final TaskSubscription sub3 = operator.openSubscription(13, 32, 123123L, 43, TASK_TYPE2_BUF);

        // when
        operator.removeSubscriptionsForChannel(13);

        // then
        assertThat(operator.getSubscription(sub1.getId())).isNull();
        assertThat(operator.getSubscription(sub2.getId())).isNotNull();
        assertThat(operator.getSubscription(sub3.getId())).isNull();
    }

    @Test
    public void shouldReturnZeroTasksForAdhocSubscription()
    {
        // given
        when(taskTypeIndex.get(argThat(hasBytes(TASK_TYPE1)), eq(0), eq(TASK_TYPE1.length), eq(-1L)))
            .thenReturn(-1L);

        final LockTasksOperator operator = new LockTasksOperator(taskTypeIndex, logReader, logWriter, dataFramePool, 1);
        operator.openAdhocSubscription(deferredResponse, 32, 123123L, 43, TASK_TYPE1_BUF);

        // when
        operator.lockTasks();

        // then
        final InOrder inOrder = inOrder(deferredResponse);

        inOrder.verify(deferredResponse).allocateAndWrite(captor.capture());

        final LockTaskBatchResponseReader reader = new LockTaskBatchResponseReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);
        assertThat(reader.consumerId()).isEqualTo(32);
        assertThat(reader.numTasks()).isEqualTo(0);

        inOrder.verify(deferredResponse).commit();
    }

    @Test
    public void shouldAcquireExactlyOnceForAdhocSubscription()
    {
        // given
        logReader.addEntry(taskWriter(13L, TaskInstanceState.NEW, TASK_TYPE1));

        when(taskTypeIndex.get(argThat(hasBytes(TASK_TYPE1)), eq(0), eq(TASK_TYPE1.length), anyLong()))
            .thenReturn(logReader.getEntryPosition(0));

        final LockTasksOperator operator = new LockTasksOperator(taskTypeIndex, logReader, logWriter, dataFramePool, 1);
        operator.openAdhocSubscription(deferredResponse, 32, 123123L, 1, TASK_TYPE1_BUF);
        when(deferredResponsePool.popDeferred()).thenReturn(deferredResponse, (DeferredResponse) null);

        operator.lockTasks();
        operator.onTaskLocked(taskReader(13L, 456L, 789L));

        logReader.addEntry(taskWriter(14L, TaskInstanceState.NEW, TASK_TYPE1));

        // when locking tasks for a second time
        final int lockedTasks = operator.lockTasks();

        // then the second task was not locked
        assertThat(lockedTasks).isEqualTo(0);
        assertThat(logWriter.size()).isEqualTo(1);
    }

    @Test
    public void shouldNotAcquireForSubscriptionsWithExhaustedCredits()
    {
        // given
        logReader.addEntry(taskWriter(13L, TaskInstanceState.NEW, TASK_TYPE1));

        when(taskTypeIndex.get(argThat(hasBytes(TASK_TYPE1)), eq(0), eq(TASK_TYPE1.length), anyLong()))
            .thenReturn(logReader.getEntryPosition(0));

        final LockTasksOperator operator = new LockTasksOperator(taskTypeIndex, logReader, logWriter, dataFramePool, 1);
        operator.openSubscription(13, 32, 123123L, 0, TASK_TYPE1_BUF);

        // when
        final int lockedTasks = operator.lockTasks();

        // then
        assertThat(lockedTasks).isEqualTo(0);
        assertThat(logWriter.size()).isEqualTo(0);
    }

    @Test
    public void shouldIgnoreLockedTaskWhenSubscriptionHasBeenClosed()
    {
        // given
        logReader.addEntry(taskWriter(123L, TaskInstanceState.NEW, TASK_TYPE1));

        when(taskTypeIndex.get(argThat(hasBytes(TASK_TYPE1)), eq(0), eq(TASK_TYPE1.length), anyLong()))
            .thenReturn(logReader.getEntryPosition(0));

        final LockTasksOperator operator = new LockTasksOperator(taskTypeIndex, logReader, logWriter, dataFramePool, 1);
        final TaskSubscription subscription = operator.openSubscription(13, 32, 123123L, 43, TASK_TYPE1_BUF);

        operator.lockTasks();

        // when
        operator.removeSubscription(subscription);

        // then the locked task for that subscription is ignored
        operator.onTaskLocked(taskReader(123L, 456L, 789L));

        verify(dataFramePool, never()).openFrame(anyInt(), anyInt());
    }

    @Test
    public void shouldAcquireTaskOnlyOnceWithMultipleSubscriptions()
    {
        // given
        final TaskInstanceWriter task = taskWriter(13L, TaskInstanceState.NEW, TASK_TYPE1);

        logReader.addEntry(task);

        when(taskTypeIndex.get(argThat(hasBytes(TASK_TYPE1)), eq(0), eq(TASK_TYPE1.length), anyLong()))
            .thenReturn(logReader.getEntryPosition(0));

        final LockTasksOperator operator = new LockTasksOperator(taskTypeIndex, logReader, logWriter, dataFramePool, 1);

        operator.openSubscription(13, 32, 123123L, 43, TASK_TYPE1_BUF);
        operator.openSubscription(14, 32, 123123L, 43, TASK_TYPE1_BUF);

        // when
        operator.lockTasks();

        // then
        assertThat(logWriter.size()).isEqualTo(1);
    }

    @Test
    public void shouldNotOpenMoreConcurrentAdhocSubscriptionsThanConfigured()
    {
        // given
        final int numConcurrentAdhocSubscriptions = 3;
        final LockTasksOperator operator = new LockTasksOperator(taskTypeIndex, logReader, logWriter, dataFramePool, numConcurrentAdhocSubscriptions);

        for (int i = 0; i < numConcurrentAdhocSubscriptions; i++)
        {
            operator.openAdhocSubscription(mock(DeferredResponse.class), 0, 0, 0, TASK_TYPE1_BUF);
        }

        // when
        final TaskSubscription subscription = operator.openAdhocSubscription(mock(DeferredResponse.class), 0, 0, 0, TASK_TYPE1_BUF);

        // then
        assertThat(subscription).isNull();
    }

    @Test
    public void shouldOpenMoreAdhocSubscriptionsThanPoolSizeSequentially()
    {
        // given
        final int numConcurrentAdhocSubscriptions = 3;
        final LockTasksOperator operator = new LockTasksOperator(taskTypeIndex, logReader, logWriter, dataFramePool, numConcurrentAdhocSubscriptions);

        final DeferredResponse response1 = mock(DeferredResponse.class);
        final TaskSubscription subscription1 = operator.openAdhocSubscription(response1, 0, 0, 0, TASK_TYPE1_BUF);
        operator.openAdhocSubscription(mock(DeferredResponse.class), 0, 0, 0, TASK_TYPE1_BUF);
        operator.openAdhocSubscription(mock(DeferredResponse.class), 0, 0, 0, TASK_TYPE1_BUF);

        // when a subscription is closed
        operator.removeSubscription(subscription1);

        // then a new subscription can be opened
        final DeferredResponse newResponse = mock(DeferredResponse.class);
        final TaskSubscription newSubscription = operator.openAdhocSubscription(newResponse, 1, 1, 1, TASK_TYPE2_BUF);

        // and the subscription object is reused
        assertThat(newSubscription).isSameAs(subscription1);

        // and the properties have been updated
        assertThat(newSubscription.getConsumerId()).isEqualTo(1);
        assertThat(newSubscription.getCredits()).isEqualTo(1L);
        assertThat(newSubscription.getLockDuration()).isEqualTo(1L);
        assertThatBuffer(newSubscription.getTaskType()).hasBytes(TASK_TYPE2);
    }



    protected TaskInstanceWriter taskWriter(long id, TaskInstanceState state, byte[] taskType)
    {
        final TaskInstanceWriter writer = new TaskInstanceWriter();
        writer.id(id);
        writer.state(state);

        final UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(taskType);
        writer.taskType(taskTypeBuffer, 0, taskTypeBuffer.capacity());

        return writer;
    }

    protected TaskInstanceReader taskReader(long id, long lockTime, long wfInstanceId)
    {
        final TaskInstanceReader reader = mock(TaskInstanceReader.class);
        when(reader.id()).thenReturn(id);
        when(reader.lockTime()).thenReturn(lockTime);
        when(reader.wfInstanceId()).thenReturn(wfInstanceId);

        return reader;
    }
}

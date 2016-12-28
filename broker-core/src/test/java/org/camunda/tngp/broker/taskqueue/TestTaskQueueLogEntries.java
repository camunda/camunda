package org.camunda.tngp.broker.taskqueue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.protocol.log.TaskInstanceDecoder;
import org.camunda.tngp.protocol.log.TaskInstanceState;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;

public class TestTaskQueueLogEntries
{

    public static final long ID = 642L;

    public static final byte[] TASK_TYPE = "ladida".getBytes(StandardCharsets.UTF_8);
    public static final int TASK_TYPE_HASH = TaskTypeHash.hashCode(TASK_TYPE, TASK_TYPE.length);

    public static final byte[] PAYLOAD = "maven".getBytes(StandardCharsets.UTF_8);

    public static TaskInstanceReader mockTaskInstance(TaskInstanceState state, long lockOwnerId, long lockTime)
    {
        final TaskInstanceReader taskReaderMock = mock(TaskInstanceReader.class);

        when(taskReaderMock.lockOwnerId()).thenReturn(lockOwnerId);
        when(taskReaderMock.lockTime()).thenReturn(lockTime);
        when(taskReaderMock.id()).thenReturn(ID);
        when(taskReaderMock.state()).thenReturn(state);
        when(taskReaderMock.prevVersionPosition()).thenReturn(TaskInstanceDecoder.prevVersionPositionNullValue());
        when(taskReaderMock.taskTypeHash()).thenReturn(Integer.toUnsignedLong(TASK_TYPE_HASH));
        when(taskReaderMock.resourceId()).thenReturn(0);
        when(taskReaderMock.shardId()).thenReturn(0);
        when(taskReaderMock.wfActivityInstanceEventKey()).thenReturn(123L);
        when(taskReaderMock.wfInstanceId()).thenReturn(123123L);
        when(taskReaderMock.wfRuntimeResourceId()).thenReturn(789);
        when(taskReaderMock.getPayload()).thenReturn(new UnsafeBuffer(PAYLOAD));
        when(taskReaderMock.getTaskType()).thenReturn(new UnsafeBuffer(TASK_TYPE));

        return taskReaderMock;
    }

    public static TaskInstanceWriter createTaskInstance(TaskInstanceState state, long lockOwnerId, long lockTime)
    {
        final TaskInstanceWriter writer = new TaskInstanceWriter();

        writer.lockOwner(lockOwnerId);
        writer.lockTime(lockTime);
        writer.id(ID);
        writer.state(state);
        writer.prevVersionPosition(TaskInstanceDecoder.prevVersionPositionNullValue());
        writer.wfActivityInstanceEventKey(123L);
        writer.wfRuntimeResourceId(789);
        writer.payload(new UnsafeBuffer(PAYLOAD), 0, PAYLOAD.length);
        writer.taskType(new UnsafeBuffer(TASK_TYPE), 0, TASK_TYPE.length);

        return writer;
    }

}

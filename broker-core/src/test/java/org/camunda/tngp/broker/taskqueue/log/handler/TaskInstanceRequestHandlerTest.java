package org.camunda.tngp.broker.taskqueue.log.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.taskqueue.TaskErrors;
import org.camunda.tngp.broker.taskqueue.TaskInstanceReader;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceRequestReader;
import org.camunda.tngp.broker.taskqueue.request.handler.TaskTypeHash;
import org.camunda.tngp.broker.test.util.ArgumentAnswer;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.broker.util.mocks.StubLogWriters;
import org.camunda.tngp.broker.util.mocks.StubResponseControl;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.protocol.error.ErrorReader;
import org.camunda.tngp.protocol.taskqueue.SingleTaskAckResponseReader;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceRequestType;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.agrona.concurrent.UnsafeBuffer;

public class TaskInstanceRequestHandlerTest
{

    protected StubLogReader logReader;
    protected StubLogWriter logWriter;
    protected StubResponseControl responseControl;
    protected StubLogWriters logWriters;

    @Mock
    protected Long2LongHashIndex lockedTasksIndex;

    protected static final byte[] PAYLOAD = "cam".getBytes(StandardCharsets.UTF_8);
    protected static final byte[] TASK_TYPE = "unda".getBytes(StandardCharsets.UTF_8);
    protected static final int TASK_TYPE_HASH = TaskTypeHash.hashCode(TASK_TYPE, TASK_TYPE.length);

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        logReader = new StubLogReader(null);
        logWriter = new StubLogWriter();
        logWriters = new StubLogWriters(0);
        logWriters.addWriter(0, logWriter);
        responseControl = new StubResponseControl();
    }

    @Test
    public void shouldCompleteTask()
    {
        // given
        final TaskInstanceRequestHandler handler = new TaskInstanceRequestHandler(logReader, lockedTasksIndex);

        final TaskInstanceWriter taskInstance = createTaskInstanceWriter(5L, 15L, TaskInstanceState.LOCKED);
        logReader.addEntry(taskInstance);
        when(lockedTasksIndex.get(eq(5L), anyLong())).thenReturn(logReader.getEntryPosition(0));

        final TaskInstanceRequestReader requestReader = mock(TaskInstanceRequestReader.class);

        when(requestReader.consumerId()).thenReturn(15L);
        when(requestReader.key()).thenReturn(5L);
        when(requestReader.type()).thenReturn(TaskInstanceRequestType.COMPLETE);

        // when
        handler.handle(requestReader, responseControl, logWriters);

        // then
        assertThat(responseControl.size()).isEqualTo(1);
        assertThat(responseControl.isAcceptance(0)).isTrue();

        final SingleTaskAckResponseReader response = responseControl.getAcceptanceValueAs(0, SingleTaskAckResponseReader.class);
        assertThat(response.taskId()).isEqualTo(5L);

        assertThat(logWriters.writtenEntries()).isEqualTo(1);
        assertThat(logWriter.size()).isEqualTo(1);
        final TaskInstanceReader updatedTaskInstance = logWriter.getEntryAs(0, TaskInstanceReader.class);

        assertThatBuffer(updatedTaskInstance.getPayload()).hasBytes(PAYLOAD);
        assertThatBuffer(updatedTaskInstance.getTaskType()).hasBytes(TASK_TYPE);
        assertThat(updatedTaskInstance.id()).isEqualTo(5L);
        assertThat(Integer.toUnsignedLong((int) updatedTaskInstance.lockOwnerId()))
            .isEqualTo(TaskInstanceEncoder.lockOwnerIdNullValue()); // https://github.com/camunda-tngp/tasks/issues/16
        assertThat(updatedTaskInstance.lockTime()).isEqualTo(TaskInstanceEncoder.lockTimeNullValue());
        assertThat(updatedTaskInstance.prevVersionPosition()).isEqualTo(0L);
        assertThat(updatedTaskInstance.resourceId()).isEqualTo(0);
        assertThat(updatedTaskInstance.shardId()).isEqualTo(0);
        assertThat(updatedTaskInstance.state()).isEqualTo(TaskInstanceState.COMPLETED);
        assertThat(updatedTaskInstance.taskTypeHash()).isEqualTo((long) TASK_TYPE_HASH);
        assertThat(updatedTaskInstance.wfActivityInstanceEventKey()).isEqualTo(123L);
        assertThat(updatedTaskInstance.wfRuntimeResourceId()).isEqualTo(789);
    }

    @Test
    public void shouldWriteErrorResponseForNonExistingTask()
    {
        // given
        final TaskInstanceRequestHandler handler = new TaskInstanceRequestHandler(logReader, lockedTasksIndex);

        when(lockedTasksIndex.get(anyLong(), anyLong())).thenAnswer(new ArgumentAnswer<Long>(1));

        final TaskInstanceRequestReader requestReader = mock(TaskInstanceRequestReader.class);

        when(requestReader.consumerId()).thenReturn(15L);
        when(requestReader.key()).thenReturn(5L);
        when(requestReader.type()).thenReturn(TaskInstanceRequestType.COMPLETE);

        // when
        handler.handle(requestReader, responseControl, logWriters);

        // then
        assertThat(responseControl.size()).isEqualTo(1);
        assertThat(responseControl.isRejection(0)).isTrue();

        final ErrorReader response = responseControl.getRejectionValueAs(0, ErrorReader.class);
        assertThat(response.componentCode()).isEqualTo(TaskErrors.COMPONENT_CODE);
        assertThat(response.detailCode()).isEqualTo(TaskErrors.COMPLETE_TASK_ERROR);
        assertThat(response.errorMessage()).isEqualTo("Task does not exist or is not locked");

        assertThat(logWriters.writtenEntries()).isZero();
    }

    @Test
    public void shouldWriteErrorResponseForWrongConsumerId()
    {
        // given
        final TaskInstanceRequestHandler handler = new TaskInstanceRequestHandler(logReader, lockedTasksIndex);

        final TaskInstanceWriter taskInstance = createTaskInstanceWriter(5L, 15L, TaskInstanceState.LOCKED);
        logReader.addEntry(taskInstance);
        when(lockedTasksIndex.get(eq(5L), anyLong())).thenReturn(logReader.getEntryPosition(0));

        final TaskInstanceRequestReader requestReader = mock(TaskInstanceRequestReader.class);

        when(requestReader.consumerId()).thenReturn(20L);
        when(requestReader.key()).thenReturn(5L);
        when(requestReader.type()).thenReturn(TaskInstanceRequestType.COMPLETE);


        // when
        handler.handle(requestReader, responseControl, logWriters);

        // then
        assertThat(responseControl.size()).isEqualTo(1);
        assertThat(responseControl.isRejection(0)).isTrue();

        final ErrorReader response = responseControl.getRejectionValueAs(0, ErrorReader.class);
        assertThat(response.componentCode()).isEqualTo(TaskErrors.COMPONENT_CODE);
        assertThat(response.detailCode()).isEqualTo(TaskErrors.COMPLETE_TASK_ERROR);
        assertThat(response.errorMessage()).isEqualTo("Task is currently not locked by the provided consumer");

        assertThat(logWriters.writtenEntries()).isEqualTo(0);
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

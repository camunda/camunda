package org.camunda.tngp.broker.taskqueue.log.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.taskqueue.TaskInstanceReader;
import org.camunda.tngp.broker.taskqueue.TestTaskQueueLogEntries;
import org.camunda.tngp.broker.taskqueue.request.handler.TaskTypeHash;
import org.camunda.tngp.broker.util.mocks.StubResponseControl;
import org.camunda.tngp.protocol.taskqueue.LockTaskBatchResponseReader;
import org.camunda.tngp.protocol.taskqueue.SingleTaskAckResponseReader;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.junit.Before;
import org.junit.Test;

public class TaskInstanceHandlerTest
{

    protected static final byte[] TASK_TYPE = "ladida".getBytes(StandardCharsets.UTF_8);
    protected static final int TASK_TYPE_HASH = TaskTypeHash.hashCode(TASK_TYPE, TASK_TYPE.length);

    protected static final byte[] PAYLOAD = "maven".getBytes(StandardCharsets.UTF_8);

    protected StubResponseControl responseControl;

    @Before
    public void setUp()
    {
        responseControl = new StubResponseControl();
    }


    @Test
    public void shouldConfirmLockedTask()
    {
        // given
        final TaskInstanceReader taskInstance = TestTaskQueueLogEntries.mockTaskInstance(TaskInstanceState.LOCKED, 42L, 53L);

        final TaskInstanceHandler handler = new TaskInstanceHandler();

        // when
        handler.handle(taskInstance, responseControl);

        // then
        assertThat(responseControl.size()).isEqualTo(1);
        assertThat(responseControl.isAcceptance(0)).isTrue();

        LockTaskBatchResponseReader responseReader = responseControl.getAcceptanceValueAs(0, LockTaskBatchResponseReader.class);

        assertThat(responseReader.consumerId()).isEqualTo(42);
        assertThat(responseReader.lockTime()).isEqualTo(53L);
        assertThat(responseReader.numTasks()).isEqualTo(1);

        responseReader = responseReader.nextTask();
        assertThat(responseReader.currentTaskId()).isEqualTo(642L);
        assertThatBuffer(responseReader.currentTaskPayload()).hasBytes(PAYLOAD);

    }


    @Test
    public void shouldConfirmNewTask()
    {
        // given
        final TaskInstanceReader taskInstance = TestTaskQueueLogEntries.mockTaskInstance(
                TaskInstanceState.NEW,
                TaskInstanceDecoder.lockOwnerIdNullValue(),
                TaskInstanceDecoder.lockTimeNullValue());

        final TaskInstanceHandler handler = new TaskInstanceHandler();

        // when
        handler.handle(taskInstance, responseControl);

        // then
        assertThat(responseControl.size()).isEqualTo(1);
        assertThat(responseControl.isAcceptance(0)).isTrue();

        final SingleTaskAckResponseReader responseReader = responseControl.getAcceptanceValueAs(0, SingleTaskAckResponseReader.class);

        assertThat(responseReader.taskId()).isEqualTo(TestTaskQueueLogEntries.ID);
    }
}

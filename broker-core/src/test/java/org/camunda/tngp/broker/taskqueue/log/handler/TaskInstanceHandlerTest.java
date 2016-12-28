package org.camunda.tngp.broker.taskqueue.log.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.taskqueue.TestTaskQueueLogEntries;
import org.camunda.tngp.broker.test.util.BufferReaderMatcher;
import org.camunda.tngp.broker.util.mocks.StubLogWriters;
import org.camunda.tngp.broker.util.mocks.StubResponseControl;
import org.camunda.tngp.protocol.log.TaskInstanceDecoder;
import org.camunda.tngp.protocol.log.TaskInstanceState;
import org.camunda.tngp.protocol.taskqueue.SingleTaskAckResponseReader;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskInstanceHandlerTest
{

    protected static final byte[] TASK_TYPE = "ladida".getBytes(StandardCharsets.UTF_8);
    protected static final int TASK_TYPE_HASH = TaskTypeHash.hashCode(TASK_TYPE, TASK_TYPE.length);

    protected static final byte[] PAYLOAD = "maven".getBytes(StandardCharsets.UTF_8);

    protected StubResponseControl responseControl;
    protected StubLogWriters logWriters;

    @Mock
    protected LockTasksOperator lockTasksOperator;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        responseControl = new StubResponseControl();
        logWriters = new StubLogWriters(0);
    }

    @Test
    public void shouldConfirmLockedTask()
    {
        // given
        final TaskInstanceReader taskInstance = TestTaskQueueLogEntries.mockTaskInstance(TaskInstanceState.LOCKED, 42L, 53L);

        final TaskInstanceHandler handler = new TaskInstanceHandler(lockTasksOperator);

        // when
        handler.handle(taskInstance, responseControl, logWriters);

        // then
        assertThat(logWriters.writtenEntries()).isZero();
        assertThat(responseControl.size()).isEqualTo(0);

        verify(lockTasksOperator).onTaskLocked(
                argThat(BufferReaderMatcher.<TaskInstanceReader>readsProperties()
                        .matching((r) -> r.lockTime(), 53L)
                        .matching((r) -> r.id(), TestTaskQueueLogEntries.ID)));
    }

    @Test
    public void shouldConfirmNewTask()
    {
        // given
        final TaskInstanceReader taskInstance = TestTaskQueueLogEntries.mockTaskInstance(
                TaskInstanceState.NEW,
                TaskInstanceDecoder.lockOwnerIdNullValue(),
                TaskInstanceDecoder.lockTimeNullValue());

        final TaskInstanceHandler handler = new TaskInstanceHandler(mock(LockTasksOperator.class));

        // when
        handler.handle(taskInstance, responseControl, logWriters);

        // then
        assertThat(logWriters.writtenEntries()).isZero();
        assertThat(responseControl.size()).isEqualTo(1);
        assertThat(responseControl.isAcceptance(0)).isTrue();

        final SingleTaskAckResponseReader responseReader = responseControl.getAcceptanceValueAs(0, SingleTaskAckResponseReader.class);

        assertThat(responseReader.taskId()).isEqualTo(TestTaskQueueLogEntries.ID);
    }
}

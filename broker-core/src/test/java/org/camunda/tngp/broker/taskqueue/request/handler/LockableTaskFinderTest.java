package org.camunda.tngp.broker.taskqueue.request.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.agrona.collections.LongHashSet;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.protocol.log.TaskInstanceState;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public class LockableTaskFinderTest
{

    protected StubLogReader logReader;

    protected static final byte[] PAYLOAD = "booom".getBytes(StandardCharsets.UTF_8);

    protected static final byte[] TASK_TYPE = "wrooom".getBytes(StandardCharsets.UTF_8);
    protected static final int TASK_TYPE_HASH = TaskTypeHash.hashCode(TASK_TYPE, TASK_TYPE.length);
    protected static final byte[] TASK_TYPE2 = "camunda".getBytes(StandardCharsets.UTF_8);

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        logReader = new StubLogReader(null)
            .addEntry(createTaskInstanceWriter(1L, TASK_TYPE, TaskInstanceState.COMPLETED))
            .addEntry(createTaskInstanceWriter(2L, TASK_TYPE2, TaskInstanceState.NEW))
            .addEntry(createTaskInstanceWriter(3L, TASK_TYPE, TaskInstanceState.NEW))
            .addEntry(createTaskInstanceWriter(4L, TASK_TYPE, TaskInstanceState.NEW));

    }

    protected TaskInstanceWriter createTaskInstanceWriter(long taskId, byte[] taskType, TaskInstanceState state)
    {
        final TaskInstanceWriter writer = new TaskInstanceWriter();

        writer.lockOwner(1L);
        writer.lockTime(2L);
        writer.id(taskId);
        writer.state(state);
        writer.prevVersionPosition(4L);
        writer.wfActivityInstanceEventKey(5L);
        writer.wfRuntimeResourceId(6);
        writer.payload(new UnsafeBuffer(PAYLOAD), 0, PAYLOAD.length);
        writer.taskType(new UnsafeBuffer(taskType), 0, taskType.length);

        return writer;
    }

    @Test
    public void shouldFindFirstLockableTask()
    {
        // given
        final LockableTaskFinder taskFinder = new LockableTaskFinder(logReader);

        final LongHashSet taskTypeQuery = new LongHashSet(-1L);
        taskTypeQuery.add(TaskTypeHash.hashCode(TASK_TYPE, TASK_TYPE.length));
        taskFinder.init(0, taskTypeQuery);

        // when
        taskFinder.findNextLockableTask();

        // then
        assertThat(taskFinder.getLockableTaskPosition()).isEqualTo(logReader.getEntryPosition(2));

        final TaskInstanceReader taskFound = taskFinder.getLockableTask();
        assertThat(taskFound).isNotNull();
        assertThat(taskFound.id()).isEqualTo(3L);
    }


}

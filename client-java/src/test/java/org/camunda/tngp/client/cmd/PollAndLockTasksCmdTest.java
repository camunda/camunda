package org.camunda.tngp.client.cmd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.ClientResponseHandler;
import org.camunda.tngp.client.impl.cmd.PollAndLockResponseHandler;
import org.camunda.tngp.client.impl.cmd.PollAndLockTasksCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.PollAndLockRequestWriter;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PollAndLockTasksCmdTest
{

    protected static final String TASK_TYPE = "conquer world";

    @Mock
    protected PollAndLockRequestWriter requestWriter;

    @Mock
    protected ClientCmdExecutor commandExecutor;


    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldSetProperties()
    {
        // given
        final PollAndLockTasksCmdImpl command = new PollAndLockTasksCmdImpl(commandExecutor);
        command.setRequestWriter(requestWriter);
        final PollAndLockAsyncTasksCmd apiCommand = command;

        // when
        apiCommand
            .lockTime(1234L)
            .maxTasks(45)
            .taskQueueId(54)
            .taskType(TASK_TYPE);

        // then
        verify(requestWriter).lockTimeMs(1234L);
        verify(requestWriter).maxTasks(45);
        verify(requestWriter).resourceId(54);
        final byte[] taskTypeBytes = TASK_TYPE.getBytes(StandardCharsets.UTF_8);
        verify(requestWriter).taskType(taskTypeBytes, 0, taskTypeBytes.length);
    }

    @Test
    public void testRequestWriter()
    {
        // given
        final PollAndLockTasksCmdImpl command = new PollAndLockTasksCmdImpl(commandExecutor);

        // when
        final BufferWriter requestWriter = command.getRequestWriter();

        // then
        assertThat(requestWriter).isInstanceOf(PollAndLockRequestWriter.class);
    }

    @Test
    public void testResponseHandlers()
    {
        // given
        final PollAndLockTasksCmdImpl command = new PollAndLockTasksCmdImpl(commandExecutor);

        // when
        final ClientResponseHandler<LockedTasksBatch> responseHandler = command.getResponseHandler();

        // then
        assertThat(responseHandler).isInstanceOf(PollAndLockResponseHandler.class);
    }
}

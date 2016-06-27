package org.camunda.tngp.client.cmd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.ClientResponseHandler;
import org.camunda.tngp.client.impl.cmd.StartWorkflowInstanceCmdImpl;
import org.camunda.tngp.client.impl.cmd.StartWorkflowInstanceResponseHandler;
import org.camunda.tngp.client.impl.cmd.wf.start.StartWorkflowInstanceRequestWriter;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class StartWorkflowInstanceCmdTest
{

    @Mock
    protected StartWorkflowInstanceRequestWriter requestWriter;

    @Mock
    protected ClientCmdExecutor commandExecutor;

    protected static final byte[] WORKFLOW_KEY_BYTES = "bar".getBytes(StandardCharsets.UTF_8);

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldSetWorkflowId()
    {
        // given
        final StartWorkflowInstanceCmdImpl command = new StartWorkflowInstanceCmdImpl(commandExecutor);
        command.setRequestWriter(requestWriter);
        final StartWorkflowInstanceCmd apiCommand = command;

        // when
        apiCommand.workflowTypeId(1234L);

        // then
        verify(requestWriter).wfTypeId(1234L);
    }

    @Test
    public void shouldSetWorkflowKeyAsBytes()
    {
        // given
        final StartWorkflowInstanceCmdImpl command = new StartWorkflowInstanceCmdImpl(commandExecutor);
        command.setRequestWriter(requestWriter);
        final StartWorkflowInstanceCmd apiCommand = command;

        // when
        apiCommand.workflowTypeKey(WORKFLOW_KEY_BYTES);

        // then
        verify(requestWriter).wfTypeKey(WORKFLOW_KEY_BYTES);
    }

    @Test
    public void shouldSetWorkflowKeyAsString()
    {
        // given
        final StartWorkflowInstanceCmdImpl command = new StartWorkflowInstanceCmdImpl(commandExecutor);
        command.setRequestWriter(requestWriter);
        final StartWorkflowInstanceCmd apiCommand = command;

        // when
        apiCommand.workflowTypeKey("bar");

        // then
        verify(requestWriter).wfTypeKey(WORKFLOW_KEY_BYTES);
    }

    @Test
    public void testRequestWriter()
    {
        // given
        final StartWorkflowInstanceCmdImpl command = new StartWorkflowInstanceCmdImpl(commandExecutor);

        // when
        final BufferWriter requestWriter = command.getRequestWriter();

        // then
        assertThat(requestWriter).isInstanceOf(StartWorkflowInstanceRequestWriter.class);
    }

    @Test
    public void testResponseHandlers()
    {
        // given
        final StartWorkflowInstanceCmdImpl command = new StartWorkflowInstanceCmdImpl(commandExecutor);

        // when
        final ClientResponseHandler<WorkflowInstance> responseHandler = command.getResponseHandler();

        // then
        assertThat(responseHandler).isInstanceOf(StartWorkflowInstanceResponseHandler.class);
    }

}

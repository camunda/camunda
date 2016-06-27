package org.camunda.tngp.client.cmd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.tngp.client.impl.cmd.StartWorkflowInstanceResponseHandler;
import org.camunda.tngp.client.impl.cmd.wf.start.StartWorkflowInstanceResponseReader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import uk.co.real_logic.agrona.DirectBuffer;

public class StartWorkflowInstanceResponseHandlerTest
{

    @Mock
    protected DirectBuffer responseBuffer;

    @Mock
    protected StartWorkflowInstanceResponseReader responseReader;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testReadResponseBody()
    {
        // given
        final StartWorkflowInstanceResponseHandler responseHandler = new StartWorkflowInstanceResponseHandler();

        responseHandler.setResponseReader(responseReader);

        when(responseReader.wfInstanceId()).thenReturn(98765L);

        // when
        final WorkflowInstance response = responseHandler.readResponse(responseBuffer, 65, 30);

        // then
        assertThat(response.getId()).isEqualTo(98765L);
        verify(responseReader).wrap(responseBuffer, 65, 30);
    }
}

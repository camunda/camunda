package org.camunda.tngp.client.cmd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.tngp.client.impl.cmd.wf.deploy.DeployBpmnResourceAckResponseHandler;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceAckResponseReader;
import org.junit.Test;

import uk.co.real_logic.agrona.DirectBuffer;

public class DeployBpmnResourceResponseHandlerTest
{

    public static final int DEFAULT_SHARD_ID = 0;
    public static final int DEFAULT_RESOURCE_ID = 0;

    public static final byte[] RESOURCE = new byte[]{122, 52, 74};

    @Test
    public void testReadResponseBody()
    {
        // given
        final DeployBpmnResourceAckResponseHandler responseHandler = new DeployBpmnResourceAckResponseHandler();

        final DeployBpmnResourceAckResponseReader responseReaderMock = mock(DeployBpmnResourceAckResponseReader.class);
        responseHandler.setResponseReader(responseReaderMock);

        when(responseReaderMock.wfTypeId()).thenReturn(98765L);
        final DirectBuffer responseBuffer = mock(DirectBuffer.class);

        // when
        final DeployedWorkflowType response = responseHandler.readResponse(responseBuffer, 15, 30);

        // then
        assertThat(response.getWorkflowTypeId()).isEqualTo(98765L);
        verify(responseReaderMock).wrap(responseBuffer, 15, 30);
    }
}

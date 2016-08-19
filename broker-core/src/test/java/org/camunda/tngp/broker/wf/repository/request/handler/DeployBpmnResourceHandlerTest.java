package org.camunda.tngp.broker.wf.repository.request.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.log.LogEntryHeaderReader;
import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.test.util.BufferWriterMatcher;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.broker.wf.WfErrors;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionRequestReader;
import org.camunda.tngp.protocol.error.ErrorReader;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceRequestReader;
import org.camunda.tngp.taskqueue.data.WfDefinitionRequestType;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class DeployBpmnResourceHandlerTest
{

    protected WfRepositoryContext context;

    protected StubLogWriter logWriter;

    @Mock
    protected DeployBpmnResourceRequestReader requestReader;

    @Mock
    protected DirectBuffer message;

    @Mock
    protected DeferredResponse response;

    public static final byte[] RESOURCE = "oh say can you see".getBytes(StandardCharsets.UTF_8);

    @Before
    public void setup()
    {
        MockitoAnnotations.initMocks(this);

        context = new MockedWfRepositoryContext();

        logWriter = new StubLogWriter();
        context.setLogWriter(logWriter);
    }

    @Test
    public void shouldWriteValidRequestToLog()
    {
        // given
        final DeployBpmnResourceHandler handler = new DeployBpmnResourceHandler();

        when(requestReader.getResource()).thenReturn(new UnsafeBuffer(RESOURCE));

        handler.requestReader = requestReader;

        // when
        handler.onRequest(context, message, 123, 456, response);

        // then
        verify(response).defer();
        verifyNoMoreInteractions(response);

        assertThat(logWriter.size()).isEqualTo(1);

        final LogEntryHeaderReader logRequestHeaderReader = logWriter.getEntryAs(0, LogEntryHeaderReader.class);
        assertThat(logRequestHeaderReader.source()).isEqualByComparingTo(EventSource.API);

        final WfDefinitionRequestReader logRequestReader = new WfDefinitionRequestReader();
        logRequestHeaderReader.readInto(logRequestReader);
        assertThatBuffer(logRequestReader.resource()).hasBytes(RESOURCE);
        assertThat(logRequestReader.type()).isEqualTo(WfDefinitionRequestType.NEW);
    }

    @Test
    public void shouldValidateResourceIsPresent()
    {
        // given
        final DeployBpmnResourceHandler handler = new DeployBpmnResourceHandler();

        when(requestReader.getResource()).thenReturn(new UnsafeBuffer(0, 0));

        handler.requestReader = requestReader;

        // when
        handler.onRequest(context, message, 123, 456, response);

        // then
        final InOrder inOrder = inOrder(response);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(ErrorReader.class)
                    .matching((e) -> e.componentCode(), WfErrors.COMPONENT_CODE)
                    .matching((e) -> e.detailCode(), WfErrors.DEPLOYMENT_REQUEST_ERROR)
                    .matching((e) -> e.errorMessage(), "Deployment resource is required")));

        inOrder.verify(response).commit();

        verifyNoMoreInteractions(response);
        assertThat(logWriter.size()).isEqualTo(0);
    }


}

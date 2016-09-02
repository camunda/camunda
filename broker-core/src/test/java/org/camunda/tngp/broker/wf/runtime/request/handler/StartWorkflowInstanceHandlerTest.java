package org.camunda.tngp.broker.wf.runtime.request.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryHeaderReader;
import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.test.util.BufferWriterMatcher;
import org.camunda.tngp.broker.test.util.FluentAnswer;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.broker.wf.WfErrors;
import org.camunda.tngp.broker.wf.runtime.MockWfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.StartWorkflowInstanceRequestReader;
import org.camunda.tngp.broker.wf.runtime.log.WorkflowInstanceRequestReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.protocol.error.ErrorReader;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.wf.Constants;
import org.camunda.tngp.protocol.wf.StartWorkflowInstanceEncoder;
import org.camunda.tngp.taskqueue.data.ProcessInstanceRequestType;
import org.camunda.tngp.taskqueue.data.WorkflowInstanceRequestDecoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class StartWorkflowInstanceHandlerTest
{

    protected MockWfRuntimeContext mockContext;
    protected StubLogWriter logWriter;
    protected IdGenerator idGenerator;

    @Mock
    protected ProcessGraph process;

    @Mock
    protected DeferredResponse response;

    @Mock
    protected DirectBuffer requestBuffer;

    @Mock
    protected StartWorkflowInstanceRequestReader requestReader;

    protected ErrorWriter errorWriter;

    protected BpmnFlowElementEventWriter flowElementEventWriter;

    public static final byte[] KEY = "myProcessId".getBytes(StandardCharsets.UTF_8);

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        flowElementEventWriter = mock(BpmnFlowElementEventWriter.class, new FluentAnswer());
        errorWriter = mock(ErrorWriter.class, new FluentAnswer());

        mockContext = new MockWfRuntimeContext();
        logWriter = new StubLogWriter();
        mockContext.setLogWriter(logWriter);

        when(process.intialFlowNodeId()).thenReturn(987);
        when(process.id()).thenReturn(1234L);

        when(response.defer()).thenReturn(1);
    }

    @Test
    public void shouldWriteValidRequestByIdToLog()
    {
        // given
        final StartWorkflowInstanceHandler handler = new StartWorkflowInstanceHandler();

        when(requestReader.wfDefinitionId()).thenReturn(123L);
        when(requestReader.wfDefinitionKey()).thenReturn(new UnsafeBuffer(0, 0));

        handler.requestReader = requestReader;

        // when
        handler.onRequest(mockContext, requestBuffer, 0, 0, response);

        // then
        verify(response).defer();

        assertThat(logWriter.size()).isEqualTo(1);

        final LogEntryHeaderReader headerReader = logWriter.getEntryAs(0, LogEntryHeaderReader.class);
        assertThat(headerReader.source()).isEqualByComparingTo(EventSource.API);

        final WorkflowInstanceRequestReader entryReader = logWriter.getEntryAs(0, WorkflowInstanceRequestReader.class);
        assertThat(entryReader.wfDefinitionId()).isEqualTo(123L);
        assertThatBuffer(entryReader.wfDefinitionKey()).hasCapacity(0);
        assertThat(entryReader.type()).isEqualByComparingTo(ProcessInstanceRequestType.NEW);
    }

    @Test
    public void shouldWriteValidRequestByKeyToLog()
    {
        // given
        final StartWorkflowInstanceHandler handler = new StartWorkflowInstanceHandler();

        when(requestReader.wfDefinitionId()).thenReturn(StartWorkflowInstanceEncoder.wfDefinitionIdNullValue());
        when(requestReader.wfDefinitionKey()).thenReturn(new UnsafeBuffer(KEY));

        handler.requestReader = requestReader;

        // when
        handler.onRequest(mockContext, requestBuffer, 0, 0, response);

        // then
        verify(response).defer();

        assertThat(logWriter.size()).isEqualTo(1);

        final LogEntryHeaderReader headerReader = logWriter.getEntryAs(0, LogEntryHeaderReader.class);
        assertThat(headerReader.source()).isEqualByComparingTo(EventSource.API);

        final WorkflowInstanceRequestReader entryReader = logWriter.getEntryAs(0, WorkflowInstanceRequestReader.class);
        assertThat(entryReader.wfDefinitionId()).isEqualTo(WorkflowInstanceRequestDecoder.wfDefinitionIdNullValue());
        assertThatBuffer(entryReader.wfDefinitionKey()).hasBytes(KEY);
        assertThat(entryReader.type()).isEqualByComparingTo(ProcessInstanceRequestType.NEW);
    }

    @Test
    public void shouldWriteRejectRequestWithBothKeyAndId()
    {
        // given
        final StartWorkflowInstanceHandler handler = new StartWorkflowInstanceHandler();

        when(requestReader.wfDefinitionId()).thenReturn(123L);
        when(requestReader.wfDefinitionKey()).thenReturn(new UnsafeBuffer(KEY));

        handler.requestReader = requestReader;

        // when
        handler.onRequest(mockContext, requestBuffer, 0, 0, response);

        // then
        final InOrder inOrder = Mockito.inOrder(response);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(ErrorReader.class)
                    .matching((r) -> r.componentCode(), WfErrors.COMPONENT_CODE)
                    .matching((r) -> r.detailCode(), WfErrors.PROCESS_INSTANCE_REQUEST_ERROR)
                    .matching((r) -> r.errorMessage(), "Only one parameter, workflow definition id or key, can be specified")
                ));

        verify(response).commit();
        verifyNoMoreInteractions(response);

        assertThat(logWriter.size()).isEqualTo(0);
    }

    @Test
    public void shouldWriteRejectRequestWithNoKeyAndId()
    {
        // given
        final StartWorkflowInstanceHandler handler = new StartWorkflowInstanceHandler();

        when(requestReader.wfDefinitionId()).thenReturn(StartWorkflowInstanceEncoder.wfDefinitionIdNullValue());
        when(requestReader.wfDefinitionKey()).thenReturn(new UnsafeBuffer(0, 0));

        handler.requestReader = requestReader;

        // when
        handler.onRequest(mockContext, requestBuffer, 0, 0, response);

        // then
        final InOrder inOrder = Mockito.inOrder(response);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(ErrorReader.class)
                    .matching((r) -> r.componentCode(), WfErrors.COMPONENT_CODE)
                    .matching((r) -> r.detailCode(), WfErrors.PROCESS_INSTANCE_REQUEST_ERROR)
                    .matching((r) -> r.errorMessage(), "Either workflow definition id or key must be specified")
                ));

        verify(response).commit();
        verifyNoMoreInteractions(response);

        assertThat(logWriter.size()).isEqualTo(0);
    }

    @Test
    public void shouldWriteRejectRequestWithTooLongKey()
    {
        // given
        final StartWorkflowInstanceHandler handler = new StartWorkflowInstanceHandler();

        when(requestReader.wfDefinitionId()).thenReturn(StartWorkflowInstanceEncoder.wfDefinitionIdNullValue());
        when(requestReader.wfDefinitionKey()).thenReturn(new UnsafeBuffer(new byte[Constants.WF_DEF_KEY_MAX_LENGTH + 1]));

        handler.requestReader = requestReader;

        // when
        handler.onRequest(mockContext, requestBuffer, 0, 0, response);

        // then
        final InOrder inOrder = Mockito.inOrder(response);

        inOrder.verify(response).allocateAndWrite(argThat(
                BufferWriterMatcher.writesProperties(ErrorReader.class)
                    .matching((r) -> r.componentCode(), WfErrors.COMPONENT_CODE)
                    .matching((r) -> r.detailCode(), WfErrors.PROCESS_INSTANCE_REQUEST_ERROR)
                    .matching((r) -> r.errorMessage(), "Workflow definition key must not be longer than " +
                            Constants.WF_DEF_KEY_MAX_LENGTH + " bytes")
                ));

        verify(response).commit();
        verifyNoMoreInteractions(response);

        assertThat(logWriter.size()).isEqualTo(0);
    }

}

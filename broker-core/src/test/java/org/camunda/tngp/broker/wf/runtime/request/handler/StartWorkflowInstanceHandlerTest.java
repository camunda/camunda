package org.camunda.tngp.broker.wf.runtime.request.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.logstreams.LogEntryHeaderReader;
import org.camunda.tngp.broker.logstreams.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.test.util.BufferWriterUtil;
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
import org.camunda.tngp.protocol.log.ProcessInstanceRequestType;
import org.camunda.tngp.protocol.log.WorkflowInstanceRequestDecoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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

    @Captor
    protected ArgumentCaptor<BufferWriter> captor;

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

        when(response.deferFifo()).thenReturn(1);
    }

    @Test
    public void shouldWriteValidRequestByIdToLog()
    {
        // given
        final StartWorkflowInstanceHandler handler = new StartWorkflowInstanceHandler();

        when(requestReader.wfDefinitionId()).thenReturn(123L);
        when(requestReader.wfDefinitionKey()).thenReturn(new UnsafeBuffer(0, 0));
        when(requestReader.payload()).thenReturn(new UnsafeBuffer(0, 0));

        handler.requestReader = requestReader;

        // when
        handler.onRequest(mockContext, requestBuffer, 0, 0, response);

        // then
        verify(response).deferFifo();

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
        when(requestReader.payload()).thenReturn(new UnsafeBuffer(0, 0));

        handler.requestReader = requestReader;

        // when
        handler.onRequest(mockContext, requestBuffer, 0, 0, response);

        // then
        verify(response).deferFifo();

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
        final InOrder inOrder = inOrder(response);
        inOrder.verify(response).allocateAndWrite(captor.capture());
        inOrder.verify(response).commit();
        verifyNoMoreInteractions(response);

        final ErrorReader reader = new ErrorReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);

        assertThat(reader.componentCode()).isEqualTo(WfErrors.COMPONENT_CODE);
        assertThat(reader.detailCode()).isEqualTo(WfErrors.PROCESS_INSTANCE_REQUEST_ERROR);
        assertThat(reader.errorMessage()).isEqualTo("Only one parameter, workflow definition id or key, can be specified");

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
        final InOrder inOrder = inOrder(response);
        inOrder.verify(response).allocateAndWrite(captor.capture());
        inOrder.verify(response).commit();
        verifyNoMoreInteractions(response);

        final ErrorReader reader = new ErrorReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);

        assertThat(reader.componentCode()).isEqualTo(WfErrors.COMPONENT_CODE);
        assertThat(reader.detailCode()).isEqualTo(WfErrors.PROCESS_INSTANCE_REQUEST_ERROR);
        assertThat(reader.errorMessage()).isEqualTo("Either workflow definition id or key must be specified");

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
        final InOrder inOrder = inOrder(response);
        inOrder.verify(response).allocateAndWrite(captor.capture());
        inOrder.verify(response).commit();
        verifyNoMoreInteractions(response);

        final ErrorReader reader = new ErrorReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);

        assertThat(reader.componentCode()).isEqualTo(WfErrors.COMPONENT_CODE);
        assertThat(reader.detailCode()).isEqualTo(WfErrors.PROCESS_INSTANCE_REQUEST_ERROR);
        assertThat(reader.errorMessage()).isEqualTo("Workflow definition key must not be longer than " +
                Constants.WF_DEF_KEY_MAX_LENGTH + " bytes");

        assertThat(logWriter.size()).isEqualTo(0);
    }

}

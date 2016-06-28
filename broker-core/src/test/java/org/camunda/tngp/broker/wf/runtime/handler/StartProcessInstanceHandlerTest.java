package org.camunda.tngp.broker.wf.runtime.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.test.util.FluentAnswer;
import org.camunda.tngp.broker.wf.WfErrors;
import org.camunda.tngp.broker.wf.repository.WfTypeCacheService;
import org.camunda.tngp.broker.wf.runtime.BpmnFlowElementEventWriter;
import org.camunda.tngp.broker.wf.runtime.MockWfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.StartProcessInstanceRequestReader;
import org.camunda.tngp.broker.wf.runtime.StartProcessInstanceResponseWriter;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.wf.runtime.StartWorkflowInstanceDecoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class StartProcessInstanceHandlerTest
{

    protected MockWfRuntimeContext mockContext;
    protected LogReader logReader;
    protected LogWriter logWriter;
    protected IdGenerator idGenerator;
    protected WfTypeCacheService processCache;

    @Mock
    protected ProcessGraph process;

    @Mock
    protected DeferredResponse response;

    @Mock
    protected DirectBuffer requestBuffer;

    @Mock
    protected StartProcessInstanceRequestReader requestReader;

    @Mock
    protected StartProcessInstanceResponseWriter responseWriter;

    protected ErrorWriter errorWriter;

    protected BpmnFlowElementEventWriter flowElementEventWriter;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        flowElementEventWriter = mock(BpmnFlowElementEventWriter.class, new FluentAnswer());
        errorWriter = mock(ErrorWriter.class, new FluentAnswer());

        mockContext = new MockWfRuntimeContext();
        logReader = mockContext.getLogReader();
        logWriter = mockContext.getLogWriter();
        idGenerator = mockContext.getIdGenerator();
        processCache = mockContext.getWfTypeCacheService();

        when(processCache.getProcessGraphByTypeId(1234L)).thenReturn(process);

        when(process.intialFlowNodeId()).thenReturn(987);
        when(process.id()).thenReturn(1234L);

        when(response.defer(anyLong(), any())).thenReturn(1);
    }

    @Test
    public void shouldStartProcessById()
    {
        // given
        final StartProcessInstanceHandler startHandler = new StartProcessInstanceHandler();

        startHandler.requestReader = requestReader;
        startHandler.responseWriter = responseWriter;
        startHandler.flowElementEventWriter = flowElementEventWriter;

        when(requestReader.wfTypeId()).thenReturn(1234L);

        when(response.allocateAndWrite(any())).thenReturn(true);

        // when
        final long result = startHandler.onRequest(mockContext, requestBuffer, 1234, 4567, response);

        // then
        assertThat(result).isGreaterThan(0);

        final ArgumentCaptor<Long> processInstanceId = ArgumentCaptor.forClass(Long.class);

        verify(flowElementEventWriter).eventType(ExecutionEventType.EVT_OCCURRED);
        verify(flowElementEventWriter).flowElementId(987);
        verify(flowElementEventWriter).processId(1234L);
        verify(flowElementEventWriter).processInstanceId(processInstanceId.capture());

        final InOrder inOrder = Mockito.inOrder(response, logWriter, requestReader, responseWriter);

        inOrder.verify(requestReader).wrap(requestBuffer, 1234, 4567);
        inOrder.verify(responseWriter).processInstanceId(processInstanceId.getValue());
        inOrder.verify(response).allocateAndWrite(responseWriter);
        inOrder.verify(logWriter).write(flowElementEventWriter);
        inOrder.verify(response).defer(anyLong(), eq(startHandler));

        verify(response, never()).commit();
        verify(response, never()).abort();
    }

    @Test
    public void shouldStartProcessByKey()
    {
        // given
        final StartProcessInstanceHandler startHandler = new StartProcessInstanceHandler();
        startHandler.requestReader = requestReader;
        startHandler.responseWriter = responseWriter;
        startHandler.flowElementEventWriter = flowElementEventWriter;

        when(requestReader.wfTypeId()).thenReturn(StartWorkflowInstanceDecoder.wfTypeIdNullValue());
        when(requestReader.wfTypeKey()).thenReturn(new UnsafeBuffer(new byte[]{ 1, 2, 3 }));
        when(processCache.getProcessGraphByTypeId(anyLong())).thenReturn(null);
        when(processCache.getLatestProcessGraphByTypeKey(any(), anyInt(), anyInt())).thenReturn(process);

        when(response.allocateAndWrite(any())).thenReturn(true);

        // when
        final long result = startHandler.onRequest(mockContext, requestBuffer, 1234, 4567, response);

        // then
        assertThat(result).isGreaterThan(0);

        final ArgumentCaptor<Long> processInstanceId = ArgumentCaptor.forClass(Long.class);

        verify(flowElementEventWriter).eventType(ExecutionEventType.EVT_OCCURRED);
        verify(flowElementEventWriter).flowElementId(987);
        verify(flowElementEventWriter).processId(1234L);
        verify(flowElementEventWriter).processInstanceId(processInstanceId.capture());


        final InOrder inOrder = Mockito.inOrder(response, logWriter, requestReader, responseWriter);

        inOrder.verify(requestReader).wrap(requestBuffer, 1234, 4567);
        inOrder.verify(responseWriter).processInstanceId(processInstanceId.getValue());
        inOrder.verify(response).allocateAndWrite(responseWriter);
        inOrder.verify(logWriter).write(flowElementEventWriter);
        inOrder.verify(response).defer(anyLong(), eq(startHandler));

        verify(response, never()).commit();
        verify(response, never()).abort();
    }

    @Test
    public void shouldWriteErrorWhenProcessNotFoundById()
    {
        // given
        final StartProcessInstanceHandler startHandler = new StartProcessInstanceHandler();
        startHandler.requestReader = requestReader;
        startHandler.errorWriter = errorWriter;
        startHandler.flowElementEventWriter = flowElementEventWriter;

        when(requestReader.wfTypeId()).thenReturn(1234L);
        when(processCache.getProcessGraphByTypeId(1234L)).thenReturn(null);

        when(response.allocateAndWrite(any())).thenReturn(true);

        // when
        final long result = startHandler.onRequest(mockContext, requestBuffer, 1234, 4567, response);

        // then
        assertThat(result).isGreaterThan(0);

        verify(errorWriter).componentCode(WfErrors.COMPONENT_CODE);
        verify(errorWriter).detailCode(WfErrors.PROCESS_NOT_FOUND_ERROR);
        verify(errorWriter).errorMessage("Cannot find process with id");

        final InOrder inOrder = inOrder(response, errorWriter);
        inOrder.verify(errorWriter).componentCode(anyInt());
        inOrder.verify(response).allocateAndWrite(errorWriter);
        inOrder.verify(response).commit();

        verifyZeroInteractions(logWriter);
    }

    @Test
    public void shouldWriteErrorWhenProcessNotFoundByKey()
    {
        // given
        final StartProcessInstanceHandler startHandler = new StartProcessInstanceHandler();
        startHandler.requestReader = requestReader;
        startHandler.errorWriter = errorWriter;
        startHandler.flowElementEventWriter = flowElementEventWriter;

        when(requestReader.wfTypeId()).thenReturn(StartWorkflowInstanceDecoder.wfTypeIdNullValue());
        when(requestReader.wfTypeKey()).thenReturn(new UnsafeBuffer(new byte[]{ 1, 2, 3 }));
        when(processCache.getProcessGraphByTypeId(anyLong())).thenReturn(null);
        when(processCache.getLatestProcessGraphByTypeKey(any(), anyInt(), anyInt())).thenReturn(null);

        when(response.allocateAndWrite(any())).thenReturn(true);

        // when
        final long result = startHandler.onRequest(mockContext, requestBuffer, 1234, 4567, response);

        // then
        assertThat(result).isGreaterThan(0);

        verify(errorWriter).componentCode(WfErrors.COMPONENT_CODE);
        verify(errorWriter).detailCode(WfErrors.PROCESS_NOT_FOUND_ERROR);
        verify(errorWriter).errorMessage("Cannot find process with key");

        final InOrder inOrder = inOrder(response, errorWriter);
        inOrder.verify(errorWriter).componentCode(anyInt());
        inOrder.verify(response).allocateAndWrite(errorWriter);
        inOrder.verify(response).commit();

        verifyZeroInteractions(logWriter);
    }

    @Test
    public void shouldReturnFailureResultWhenResponseNotAllocated()
    {
        // given
        final StartProcessInstanceHandler startHandler = new StartProcessInstanceHandler();
        startHandler.requestReader = requestReader;
        startHandler.errorWriter = errorWriter;
        startHandler.flowElementEventWriter = flowElementEventWriter;

        when(requestReader.wfTypeId()).thenReturn(1234L);
        when(processCache.getProcessGraphByTypeId(1234L)).thenReturn(null);

        when(response.allocateAndWrite(any())).thenReturn(false);

        // when
        final long result = startHandler.onRequest(mockContext, requestBuffer, 0, 0, response);

        // then
        assertThat(result).isLessThan(0);

        verify(response, never()).defer(anyLong(), any());
        verify(response, never()).commit();
        verify(response, never()).abort();
    }

    @Test
    public void shouldReturnFailureResultWhenResponseNotDeferred()
    {
        // given
        final StartProcessInstanceHandler startHandler = new StartProcessInstanceHandler();
        startHandler.requestReader = requestReader;
        startHandler.errorWriter = errorWriter;
        startHandler.flowElementEventWriter = flowElementEventWriter;

        when(requestReader.wfTypeId()).thenReturn(1234L);

        when(response.allocateAndWrite(any())).thenReturn(true);
        when(response.defer(anyLong(), any())).thenReturn(-1);

        // when
        final long result = startHandler.onRequest(mockContext, requestBuffer, 0, 0, response);

        // then
        assertThat(result).isEqualTo(-1);

        verify(response, never()).commit();
        verify(response, never()).abort();
    }

    @Test
    public void shouldCommitResponseWhenAsyncWorkComplete()
    {
        // given
        final StartProcessInstanceHandler startHandler = new StartProcessInstanceHandler();

        // when
        startHandler.onAsyncWorkCompleted(response);

        // then
        verify(response).commit();
        verify(response, never()).abort();
    }

    @Test
    public void shouldAbortResponseWhenAsyncWorkFailed()
    {
        // given
        final StartProcessInstanceHandler startHandler = new StartProcessInstanceHandler();

        // when
        startHandler.onAsyncWorkFailed(response);

        // then
        verify(response).abort();
        verify(response, never()).commit();
    }
}

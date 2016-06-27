package org.camunda.tngp.broker.wf.runtime.handler;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.broker.wf.WfErrors;
import org.camunda.tngp.broker.wf.repository.WfTypeCacheService;
import org.camunda.tngp.broker.wf.runtime.BpmnFlowElementEventWriter;
import org.camunda.tngp.broker.wf.runtime.StartProcessInstanceRequestReader;
import org.camunda.tngp.broker.wf.runtime.StartProcessInstanceResponseWriter;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.wf.runtime.StartWorkflowInstanceDecoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.ResponseCompletionHandler;

import uk.co.real_logic.agrona.DirectBuffer;

public class StartProcessInstanceHandler implements BrokerRequestHandler<WfRuntimeContext>, ResponseCompletionHandler
{

    protected StartProcessInstanceRequestReader requestReader = new StartProcessInstanceRequestReader();
    protected StartProcessInstanceResponseWriter responseWriter = new StartProcessInstanceResponseWriter();

    protected ErrorWriter errorWriter = new ErrorWriter();

    protected BpmnFlowElementEventWriter flowElementEventWriter = new BpmnFlowElementEventWriter();

    public static final int WF_TYPE_KEY_MAX_LENGTH = 256;
    protected final byte[] keyBuffer = new byte[WF_TYPE_KEY_MAX_LENGTH];

    @Override
    public long onRequest(
            final WfRuntimeContext context,
            final DirectBuffer msg,
            final int offset,
            final int length,
            final DeferredResponse response)
    {
        final WfTypeCacheService wfTypeCache = context.getWfTypeCacheService();
        final IdGenerator idGenerator = context.getIdGenerator();
        final LogWriter logWriter = context.getLogWriter();

        requestReader.wrap(msg, offset, length);

        ProcessGraph processGraph = null;
        String errorMessage = null;

        final long processId = requestReader.wfTypeId();
        if (processId != StartWorkflowInstanceDecoder.wfTypeIdNullValue())
        {
            processGraph = wfTypeCache.getProcessGraphByTypeId(processId);

            if (processGraph == null)
            {
                errorMessage = "Cannot find process with id";
            }
        }
        else
        {
            final DirectBuffer wfTypeKey = requestReader.wfTypeKey();
            processGraph = wfTypeCache.getLatestProcessGraphByTypeKey(wfTypeKey, 0, wfTypeKey.capacity());

            if (processGraph == null)
            {
                errorMessage = "Cannot find process with key";
            }
        }

        if (processGraph != null)
        {
            return startProcess(response, logWriter, processGraph, idGenerator);
        }
        else
        {
            errorWriter
                .componentCode(WfErrors.COMPONENT_CODE)
                .detailCode(WfErrors.PROCESS_NOT_FOUND_ERROR)
                .errorMessage(errorMessage);

            if (response.allocateAndWrite(errorWriter))
            {
                response.commit();
                return 1;
            }
            else
            {
                return -1;
            }
        }
    }

    protected int startProcess(
            DeferredResponse response,
            LogWriter logWriter,
            ProcessGraph processGraph,
            IdGenerator idGenerator)
    {
        final long processInstanceId = idGenerator.nextId();
        final long eventId = idGenerator.nextId();

        responseWriter.processInstanceId(processInstanceId);

        if (response.allocateAndWrite(responseWriter))
        {
            flowElementEventWriter
                .key(eventId)
                .processInstanceId(processInstanceId)
                .processId(processGraph.id())
                .eventType(ExecutionEventType.EVT_OCCURRED)
                .flowElementId(processGraph.intialFlowNodeId());


            final long logEntryOffset = logWriter.write(flowElementEventWriter);
            return response.defer(logEntryOffset, this);
        }
        else
        {
            return -1;
        }
    }

    @Override
    public void onAsyncWorkCompleted(DeferredResponse response)
    {
        response.commit();
    }

    @Override
    public void onAsyncWorkFailed(DeferredResponse response)
    {
        response.abort();
    }

}

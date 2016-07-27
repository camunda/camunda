package org.camunda.tngp.broker.wf.runtime.handler;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.broker.wf.WfErrors;
import org.camunda.tngp.broker.wf.repository.WfDefinitionCache;
import org.camunda.tngp.broker.wf.runtime.StartWorkflowInstanceRequestReader;
import org.camunda.tngp.broker.wf.runtime.StartWorkflowInstanceResponseWriter;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventWriter;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.wf.Constants;
import org.camunda.tngp.protocol.wf.runtime.StartWorkflowInstanceDecoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.ResponseCompletionHandler;

import uk.co.real_logic.agrona.DirectBuffer;

public class StartWorkflowInstanceHandler implements BrokerRequestHandler<WfRuntimeContext>, ResponseCompletionHandler
{

    protected StartWorkflowInstanceRequestReader requestReader = new StartWorkflowInstanceRequestReader();
    protected StartWorkflowInstanceResponseWriter responseWriter = new StartWorkflowInstanceResponseWriter();

    protected ErrorWriter errorWriter = new ErrorWriter();

    protected BpmnFlowElementEventWriter flowElementEventWriter = new BpmnFlowElementEventWriter();

    protected final byte[] keyBuffer = new byte[Constants.WF_DEF_KEY_MAX_LENGTH];

    @Override
    public long onRequest(
            final WfRuntimeContext context,
            final DirectBuffer msg,
            final int offset,
            final int length,
            final DeferredResponse response)
    {
        System.out.println();
        System.out.println("Starting process instance");

        final WfDefinitionCache wfDefinitionCache = context.getWfDefinitionCache();
        final IdGenerator idGenerator = context.getIdGenerator();
        final LogWriter logWriter = context.getLogWriter();

        requestReader.wrap(msg, offset, length);

        ProcessGraph processGraph = null;
        String errorMessage = null;

        final long processId = requestReader.wfDefinitionId();
        if (processId != StartWorkflowInstanceDecoder.wfDefinitionIdNullValue())
        {
            processGraph = wfDefinitionCache.getProcessGraphByTypeId(processId);

            if (processGraph == null)
            {
                errorMessage = "Cannot find process with id";
            }
        }
        else
        {
            final DirectBuffer wfDefinitionKey = requestReader.wfDefinitionKey();
            processGraph = wfDefinitionCache.getLatestProcessGraphByTypeKey(wfDefinitionKey, 0, wfDefinitionKey.capacity());

            if (processGraph == null)
            {
                errorMessage = "Cannot find process with key";
            }
        }

        if (processGraph != null)
        {
            return startWorkflow(response, logWriter, processGraph, idGenerator);
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

    protected int startWorkflow(
            DeferredResponse response,
            LogWriter logWriter,
            ProcessGraph processGraph,
            IdGenerator idGenerator)
    {
        final long workflowInstanceId = idGenerator.nextId();
        final long eventId = idGenerator.nextId();

        responseWriter.id(workflowInstanceId);

        if (response.allocateAndWrite(responseWriter))
        {
            flowElementEventWriter
                .key(eventId)
                .workflowInstanceId(workflowInstanceId)
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

package org.camunda.tngp.broker.wf.runtime.log.handler;

import org.agrona.DirectBuffer;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.wf.WfErrors;
import org.camunda.tngp.broker.wf.runtime.StartWorkflowInstanceResponseWriter;
import org.camunda.tngp.broker.wf.runtime.WfDefinitionCache;
import org.camunda.tngp.broker.wf.runtime.log.WorkflowInstanceRequestReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventWriter;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.wf.StartWorkflowInstanceDecoder;
import org.camunda.tngp.protocol.log.ProcessInstanceRequestType;

public class WorkflowInstanceRequestHandler implements LogEntryTypeHandler<WorkflowInstanceRequestReader>
{

    protected final WfDefinitionCache wfDefinitionCache;
    protected final IdGenerator idGenerator;

    protected StartWorkflowInstanceResponseWriter responseWriter = new StartWorkflowInstanceResponseWriter();
    protected ErrorWriter errorWriter = new ErrorWriter();
    protected BpmnFlowElementEventWriter flowElementEventWriter = new BpmnFlowElementEventWriter();

    public WorkflowInstanceRequestHandler(WfDefinitionCache wfDefinitionCache, IdGenerator idGenerator)
    {
        this.wfDefinitionCache = wfDefinitionCache;
        this.idGenerator = idGenerator;
    }

    @Override
    public void handle(WorkflowInstanceRequestReader reader, ResponseControl responseControl, LogWriters logWriters)
    {
        if (reader.type() == ProcessInstanceRequestType.NEW)
        {
            createNewWorkflowInstance(reader, responseControl, logWriters);
        }
    }

    protected void createNewWorkflowInstance(WorkflowInstanceRequestReader requestReader, ResponseControl responseControl, LogWriters logWriters)
    {
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
            final long workflowInstanceId = idGenerator.nextId();
            final long eventId = idGenerator.nextId();

            responseWriter.id(workflowInstanceId);

            final DirectBuffer payload = requestReader.payload();

            // TODO: should this only be done if the response can be written? (like before)
            flowElementEventWriter
                .key(eventId)
                .workflowInstanceId(workflowInstanceId)
                .processId(processGraph.id())
                .eventType(ExecutionEventType.EVT_OCCURRED)
                .flowElementId(processGraph.intialFlowNodeId())
                .payload(payload, 0, payload.capacity());

            logWriters.writeToCurrentLog(flowElementEventWriter);
            responseControl.accept(responseWriter);
        }
        else
        {
            errorWriter
                .componentCode(WfErrors.COMPONENT_CODE)
                .detailCode(WfErrors.PROCESS_NOT_FOUND_ERROR)
                .errorMessage(errorMessage);

            responseControl.reject(errorWriter);
        }

    }

}

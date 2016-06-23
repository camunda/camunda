package org.camunda.tngp.broker.wf.runtime.handler;

import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.broker.wf.repository.WfTypeCacheService;
import org.camunda.tngp.broker.wf.runtime.BpmnFlowElementEventWriter;
import org.camunda.tngp.broker.wf.runtime.StartProcessInstanceRequestReader;
import org.camunda.tngp.broker.wf.runtime.StartProcessInstanceResponseWriter;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogEntryWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.protocol.wf.runtime.StartWorkflowInstanceDecoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.ResponseCompletionHandler;

import uk.co.real_logic.agrona.DirectBuffer;

public class StartProcessInstanceHandler implements BrokerRequestHandler<WfRuntimeContext>, ResponseCompletionHandler
{

    protected final StartProcessInstanceRequestReader requestReader = new StartProcessInstanceRequestReader();
    protected final StartProcessInstanceResponseWriter responseWriter = new StartProcessInstanceResponseWriter();

    protected final BpmnFlowElementEventWriter flowElementEventWriter = new BpmnFlowElementEventWriter();

    protected final LogEntryWriter logEntryWriter = new LogEntryWriter();

    protected final FlowElementVisitor flowElementVisitor = new FlowElementVisitor();

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
        final Log log = context.getLog();

        requestReader.wrap(msg, offset, length);

        final ProcessGraph processGraph = findProcessGraph(wfTypeCache);

        if(processGraph != null)
        {
            startProcess(response, log, processGraph, idGenerator);
        }
        else
        {
            // TODO: wf type with id not found / not deployed
            // send error response
        }

        return 0;
    }

    protected void startProcess(
            DeferredResponse response,
            Log log,
            ProcessGraph processGraph,
            IdGenerator idGenerator)
    {
        if (response.allocateAndWrite(responseWriter))
        {
            final long processInstanceId = idGenerator.nextId();
            final long eventId = idGenerator.nextId();

            flowElementEventWriter
                .key(eventId)
                .processInstanceId(processInstanceId)
                .processId(processGraph.id())
                .eventType(ExecutionEventType.PROC_INST_CREATED)
                .flowElementId(processGraph.intialFlowNodeId());

            responseWriter.processInstanceId(processInstanceId);

            long logEntryOffset = logEntryWriter.write(log, flowElementEventWriter);

            response.defer(logEntryOffset, this, null);
        }
    }

    protected ProcessGraph findProcessGraph(final WfTypeCacheService wfTypeCache)
    {
        ProcessGraph processGraph = null;

        final long processId = requestReader.wfTypeId();
        if(processId != StartWorkflowInstanceDecoder.wfTypeIdNullValue())
        {
            processGraph = wfTypeCache.getProcessGraphByTypeId(processId);

            if (processGraph == null)
            {
                // TODO: cannot find workflow type by id
            }
        }
        else
        {
            processGraph = wfTypeCache.getLatestProcessGraphByTypeKey(requestReader.wfTypeKey());

            if(processGraph == null)
            {
                // TODO: cannot find workflow type by key
            }
        }

        return processGraph;
    }

    @Override
    public void onAsyncWorkCompleted(
            DeferredResponse response,
            DirectBuffer asyncWorkBuffer,
            int offset,
            int length,
            Object attachement,
            long blockPosition)
    {
        response.commit();
    }

    @Override
    public void onAsyncWorkFailed(
            DeferredResponse response,
            DirectBuffer asyncWorkBuffer,
            int offset,
            int length,
            Object attachement)
    {
        response.abort();
    }

}

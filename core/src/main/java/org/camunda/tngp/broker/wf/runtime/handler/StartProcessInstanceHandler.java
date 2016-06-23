package org.camunda.tngp.broker.wf.runtime.handler;

import java.util.Arrays;

import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.broker.wf.repository.WfTypeCacheService;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.protocol.wf.MessageHeaderDecoder;
import org.camunda.tngp.protocol.wf.MessageHeaderEncoder;
import org.camunda.tngp.protocol.wf.StartWorkflowInstanceDecoder;
import org.camunda.tngp.protocol.wf.StartWorkflowInstanceResponseEncoder;
import org.camunda.tngp.taskqueue.data.FlowElementExecutionEventEncoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.ResponseCompletionHandler;

import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public class StartProcessInstanceHandler implements BrokerRequestHandler<WfRuntimeContext>, ResponseCompletionHandler
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final StartWorkflowInstanceDecoder requestDecoder = new StartWorkflowInstanceDecoder();
    protected final StartWorkflowInstanceResponseEncoder responseEncoder = new StartWorkflowInstanceResponseEncoder();
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    protected final ClaimedFragment claimedFragment = new ClaimedFragment();

    protected final FlowElementExecutionEventEncoder flowElementExecutionEventEncoder = new FlowElementExecutionEventEncoder();
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
        final Dispatcher logWriteBuffer = log.getWriteBuffer();

        headerDecoder.wrap(msg, offset);

        requestDecoder.wrap(msg, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());
        final ProcessGraph processGraph = findProcessGraph(wfTypeCache);

        if (response.allocate(MessageHeaderEncoder.ENCODED_LENGTH + responseEncoder.sbeBlockLength()))
        {
            if (processGraph != null)
            {
                final long claimedOffset = claimLogEntry(requestDecoder.payloadLength(), logWriteBuffer);

                if (claimedOffset >= 0)
                {
                    final long processInstanceId = idGenerator.nextId();

                    writeEvent(context, processGraph, processInstanceId);
                    claimedFragment.commit();

                    writeResponse(context, response, processInstanceId);
                    response.defer(claimedOffset, this, null);
                }
                else
                {
                    // TODO: could not claim entry in log buffer
                    // => backpressure
                    response.abort();
                }
            }
            else
            {
                // TODO: wf type with id not found / not deployed
                // send error response
            }
        }
        else
        {
            // TODO: error: could not allocate response
            // => backpressure
        }

        return 0;
    }

    protected ProcessGraph findProcessGraph(final WfTypeCacheService wfTypeCache)
    {
        ProcessGraph processGraph = null;

        final long wfTypeId = requestDecoder.wfTypeId();
        if (wfTypeId != StartWorkflowInstanceDecoder.wfTypeIdNullValue())
        {
            processGraph = wfTypeCache.getProcessGraphByTypeId(wfTypeId);

            if (processGraph == null)
            {
                // TODO: cannot find workflow type by id
            }
        }
        else
        {
            final int payloadLength = requestDecoder.payloadLength();
            if (payloadLength < WF_TYPE_KEY_MAX_LENGTH)
            {
                requestDecoder.getPayload(keyBuffer, 0, payloadLength);
                Arrays.fill(keyBuffer, payloadLength, keyBuffer.length, (byte) 0);
                processGraph = wfTypeCache.getLatestProcessGraphByTypeKey(keyBuffer);

                if (processGraph == null)
                {
                    // TODO: cannot find workflow type by key
                }
            }
            else
            {
                // TODO: error key to long
            }
        }

        return processGraph;
    }

    protected void writeEvent(final WfRuntimeContext context, ProcessGraph processGraph, final long processInstanceId)
    {
        flowElementVisitor.init(processGraph)
            .moveToNode(0);

        final MutableDirectBuffer logEntryBuffer = claimedFragment.getBuffer();
        int writeOffset = claimedFragment.getOffset();

        headerEncoder.wrap(logEntryBuffer, writeOffset)
            .blockLength(flowElementExecutionEventEncoder.sbeBlockLength())
            .templateId(flowElementExecutionEventEncoder.sbeTemplateId())
            .schemaId(flowElementExecutionEventEncoder.sbeSchemaId())
            .version(flowElementExecutionEventEncoder.sbeSchemaVersion())
            .resourceId(context.getResourceId())
            .shardId(0);

        writeOffset += headerEncoder.encodedLength();

        flowElementExecutionEventEncoder.wrap(logEntryBuffer, writeOffset)
            .key(processInstanceId)
            .event(flowElementVisitor.onEnterEvent().value())
            .processId(processGraph.id())
            .processInstanceId(processInstanceId)
            .parentFlowElementInstanceId(-1)
            .flowElementId(0)
            .flowElementType(flowElementVisitor.type().value());
    }

    protected void writeResponse(
            final WfRuntimeContext context,
            final DeferredResponse response,
            final long processInstanceId)
    {
        final MutableDirectBuffer responseBuffer = response.getBuffer();
        int writeOffset = response.getClaimedOffset();

        headerEncoder.wrap(responseBuffer, writeOffset)
            .blockLength(responseEncoder.sbeBlockLength())
            .templateId(responseEncoder.sbeTemplateId())
            .schemaId(responseEncoder.sbeSchemaId())
            .version(responseEncoder.sbeSchemaVersion())
            .resourceId(context.getResourceId())
            .shardId(0);

        writeOffset += headerEncoder.encodedLength();

        responseEncoder.wrap(responseBuffer, writeOffset)
            .wfInstanceId(processInstanceId);
    }

    private long claimLogEntry(final int payloadLength, final Dispatcher logWriteBuffer)
    {
        final int logEntryLength = MessageHeaderEncoder.ENCODED_LENGTH + flowElementExecutionEventEncoder.sbeBlockLength();

        long claimedOffset = -1;
        do
        {
            claimedOffset = logWriteBuffer.claim(claimedFragment, logEntryLength);
        }
        while (claimedOffset == -2);

        claimedOffset -= BitUtil.align(claimedFragment.getFragmentLength(), 8);

        return claimedOffset;
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

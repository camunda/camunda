package org.camunda.tngp.broker.wf.runtime;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.wf.repository.WfTypeCacheService;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogFragmentHandler;
import org.camunda.tngp.taskqueue.data.BpmnFlowElementEventDecoder;
import org.camunda.tngp.taskqueue.data.BpmnProcessEventDecoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class BpmnEventHandler implements LogFragmentHandler
{

    protected static final int READ_BUFFER_SIZE = 1024 * 1024;

    protected final ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
    protected final UnsafeBuffer readBufferView = new UnsafeBuffer(readBuffer);

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    protected final Int2ObjectHashMap<BpmnFlowElementEventHandler> flowElementEventHandlers = new Int2ObjectHashMap<BpmnFlowElementEventHandler>();
    protected final Int2ObjectHashMap<BpmnProcessEventHandler> processEventHandlers = new Int2ObjectHashMap<BpmnProcessEventHandler>();

    protected final BpmnFlowElementEventReader flowElementEventReader = new BpmnFlowElementEventReader();

    protected final WfTypeCacheService processCache;
    protected final FlowElementVisitor flowElementVisitor = new FlowElementVisitor();

    protected final Log log;
    protected long logPosition;

    public BpmnEventHandler(WfTypeCacheService processCache, Log log)
    {
        this.processCache = processCache;
        this.log = log;
        this.logPosition = log.getInitialPosition();

        addFlowElementHandler(new StartProcessHandler());
    }

    protected void addFlowElementHandler(BpmnFlowElementEventHandler handler)
    {
        flowElementEventHandlers.put(handler.getHandledBpmnAspect().value(), handler);
    }

    public int doWork()
    {
        int workCount = 0;

        long nextPosition = log.pollFragment(logPosition, this);
        if(nextPosition >= 0)
        {
            this.logPosition = nextPosition;
            ++workCount;

            DirectBuffer eventBuffer = readBufferView;

            handleBpmnEvent(eventBuffer, log);

        }

        return workCount;
    }

    @Override
    public void onFragment(long position, FileChannel fileChannel, int offset, int length)
    {

        readBuffer.position(0);
        readBuffer.limit(length);
        try
        {
            int bytesRead = fileChannel.read(readBuffer, offset);
            if (bytesRead < length)
            {
                throw new RuntimeException("Less bytes read than expected");
            }

            readBufferView.wrap(readBuffer, 0, length);

        } catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void handleBpmnEvent(DirectBuffer eventBuffer, Log targetLog)
    {
        headerDecoder.wrap(eventBuffer, 0);

        int bpmnEventType = headerDecoder.templateId();

        switch (bpmnEventType) {
            case BpmnProcessEventDecoder.TEMPLATE_ID:
                break;
            case BpmnFlowElementEventDecoder.TEMPLATE_ID:
                flowElementEventReader.wrap(eventBuffer, 0, eventBuffer.capacity());

                ProcessGraph process = processCache.getProcessGraphByTypeId(flowElementEventReader.processId());
                flowElementVisitor.init(process).moveToNode(flowElementEventReader.flowElementId());

                BpmnAspect bpmnAspect = flowElementVisitor.aspectFor(flowElementEventReader.event());
                BpmnFlowElementEventHandler handler = flowElementEventHandlers.get(bpmnAspect.value());
                handler.handle(flowElementEventReader, process, targetLog);
                break;
            default:
                throw new RuntimeException("No handler for event of type " + bpmnEventType);
        }

    }

}

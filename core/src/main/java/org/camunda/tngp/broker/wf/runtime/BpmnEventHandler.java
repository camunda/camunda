package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.wf.repository.WfTypeCacheService;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.taskqueue.data.BpmnFlowElementEventDecoder;
import org.camunda.tngp.taskqueue.data.BpmnProcessEventDecoder;

import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;

public class BpmnEventHandler
{


    protected BpmnEventReader eventReader = new BpmnEventReader();
    protected final LogReader logReader;
    protected final LogWriter logWriter;

    protected final Int2ObjectHashMap<BpmnFlowElementEventHandler> flowElementEventHandlers = new Int2ObjectHashMap<BpmnFlowElementEventHandler>();
    protected final Int2ObjectHashMap<BpmnProcessEventHandler> processEventHandlers = new Int2ObjectHashMap<BpmnProcessEventHandler>();

    protected final WfTypeCacheService processCache;
    protected FlowElementVisitor flowElementVisitor = new FlowElementVisitor();

    public BpmnEventHandler(WfTypeCacheService processCache, LogReader logReader, LogWriter logWriter)
    {
        this.processCache = processCache;
        this.logReader = logReader;
        this.logWriter = logWriter;

        // TODO: these should be listed in the wf component config
        //   => makes this class easier testable
        addFlowElementHandler(new StartProcessHandler());
    }

    public void addFlowElementHandler(BpmnFlowElementEventHandler handler)
    {
        flowElementEventHandlers.put(handler.getHandledBpmnAspect().value(), handler);
    }

    public int doWork()
    {
        final int workCount = 0;

        final boolean hasNext = logReader.read(eventReader);
        if (hasNext)
        {
            handleBpmnEvent(eventReader);
        }

        return workCount;
    }

    protected void handleBpmnEvent(BpmnEventReader eventReader)
    {
        final int bpmnEventType = eventReader.templateId();

        switch (bpmnEventType)
        {
            case BpmnProcessEventDecoder.TEMPLATE_ID:
                break;
            case BpmnFlowElementEventDecoder.TEMPLATE_ID:
                handleBpmnFlowElementEvent(eventReader.flowElementEvent());
                break;
            default:
                throw new RuntimeException("No handler for event of type " + bpmnEventType);
        }
    }

    protected void handleBpmnFlowElementEvent(BpmnFlowElementEventReader flowElementEventReader)
    {
        final ProcessGraph process = processCache.getProcessGraphByTypeId(flowElementEventReader.processId());

        flowElementVisitor.init(process).moveToNode(flowElementEventReader.flowElementId());

        final BpmnAspect bpmnAspect = flowElementVisitor.aspectFor(flowElementEventReader.event());
        final BpmnFlowElementEventHandler handler = flowElementEventHandlers.get(bpmnAspect.value());

        handler.handle(flowElementEventReader, process, logWriter);
    }

    public void setEventReader(BpmnEventReader eventReader)
    {
        this.eventReader = eventReader;
    }

    public void setFlowElementVisitor(FlowElementVisitor flowElementVisitor)
    {
        this.flowElementVisitor = flowElementVisitor;
    }
}

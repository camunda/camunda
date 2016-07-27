package org.camunda.tngp.broker.wf.runtime.bpmn.handler;

import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.broker.wf.repository.WfDefinitionCache;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.taskqueue.data.BpmnActivityEventDecoder;
import org.camunda.tngp.taskqueue.data.BpmnFlowElementEventDecoder;
import org.camunda.tngp.taskqueue.data.BpmnProcessEventDecoder;

import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;

public class BpmnEventHandler implements LogEntryHandler<BpmnEventReader>
{

    protected final LogReader logReader;
    protected final LogWriter logWriter;
    protected final IdGenerator idGenerator;

    protected final Int2ObjectHashMap<BpmnFlowElementEventHandler> flowElementEventHandlers = new Int2ObjectHashMap<>();
    protected final Int2ObjectHashMap<BpmnProcessEventHandler> processEventHandlers = new Int2ObjectHashMap<>();
    protected final Int2ObjectHashMap<BpmnActivityInstanceEventHandler> activityEventHandlers = new Int2ObjectHashMap<>();

    protected final LogEntryProcessor<BpmnEventReader> logEntryProcessor;

    protected final WfDefinitionCache processCache;
    protected FlowElementVisitor flowElementVisitor = new FlowElementVisitor();

    public BpmnEventHandler(WfDefinitionCache processCache, LogReader logReader, LogWriter logWriter, IdGenerator idGenerator)
    {
        this.processCache = processCache;
        this.logReader = logReader;
        this.logWriter = logWriter;
        this.idGenerator = idGenerator;
        this.logEntryProcessor = new LogEntryProcessor<>(logReader, new BpmnEventReader(), this);
    }

    public void addFlowElementHandler(BpmnFlowElementEventHandler handler)
    {
        flowElementEventHandlers.put(handler.getHandledBpmnAspect().value(), handler);
    }

    public void addProcessHandler(BpmnProcessEventHandler handler)
    {
        processEventHandlers.put(handler.getHandledBpmnAspect().value(), handler);
    }

    public void addActivityHandler(BpmnActivityInstanceEventHandler handler)
    {
        activityEventHandlers.put(handler.getHandledBpmnAspect().value(), handler);
    }

    public int doWork()
    {
        return logEntryProcessor.doWork(Integer.MAX_VALUE);
    }

    @Override
    public int handle(long position, BpmnEventReader reader)
    {
        final int bpmnEventType = reader.templateId();

        switch (bpmnEventType)
        {
            case BpmnProcessEventDecoder.TEMPLATE_ID:
                return handleBpmnProcessEvent(reader.processEvent());
            case BpmnFlowElementEventDecoder.TEMPLATE_ID:
                return handleBpmnFlowElementEvent(reader.flowElementEvent());
            case BpmnActivityEventDecoder.TEMPLATE_ID:
                return handleBpmnActivityEvent(reader.activityEvent());
            default:
                throw new RuntimeException("No handler for event of type " + bpmnEventType);
        }
    }

    protected int handleBpmnFlowElementEvent(BpmnFlowElementEventReader flowElementEventReader)
    {
        final ProcessGraph process = processCache.getProcessGraphByTypeId(flowElementEventReader.wfDefinitionId());

        flowElementVisitor.init(process).moveToNode(flowElementEventReader.flowElementId());

        final BpmnAspect bpmnAspect = flowElementVisitor.aspectFor(flowElementEventReader.event());
        final BpmnFlowElementEventHandler handler = flowElementEventHandlers.get(bpmnAspect.value());

        System.out.println("Handling event of type " + flowElementEventReader.event());

        return handler.handle(flowElementEventReader, process, logWriter, idGenerator);
    }

    protected int handleBpmnProcessEvent(BpmnProcessEventReader processEventReader)
    {
        final ProcessGraph process = processCache.getProcessGraphByTypeId(processEventReader.processId());

        // TODO: move to process node; how to find it? Do we need to store the node id (!= process id) in the process event?
        flowElementVisitor.init(process).moveToNode(0);

        final BpmnAspect bpmnAspect = flowElementVisitor.aspectFor(processEventReader.event());
        final BpmnProcessEventHandler handler = processEventHandlers.get(bpmnAspect.value());

        System.out.println("Handling event of type " + processEventReader.event());

        return handler.handle(processEventReader, process, logWriter, idGenerator);
    }

    protected int handleBpmnActivityEvent(BpmnActivityEventReader activityEventReader)
    {
        final ProcessGraph process = processCache.getProcessGraphByTypeId(activityEventReader.wfDefinitionId());

        flowElementVisitor.init(process).moveToNode(activityEventReader.flowElementId());

        final BpmnAspect bpmnAspect = flowElementVisitor.aspectFor(activityEventReader.event());
        final BpmnActivityInstanceEventHandler handler = activityEventHandlers.get(bpmnAspect.value());

        System.out.println("Handling event of type " + activityEventReader.event());

        return handler.handle(activityEventReader, process, logWriter, idGenerator);
    }

    public void setFlowElementVisitor(FlowElementVisitor flowElementVisitor)
    {
        this.flowElementVisitor = flowElementVisitor;
    }

}

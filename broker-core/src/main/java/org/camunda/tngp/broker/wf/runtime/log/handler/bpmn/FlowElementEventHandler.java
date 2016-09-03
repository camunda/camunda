package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import org.agrona.collections.Int2ObjectHashMap;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.wf.repository.WfDefinitionCache;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class FlowElementEventHandler implements LogEntryTypeHandler<BpmnFlowElementEventReader>
{
    public static final String DEBUG_LOGGING_ENABLED_PROP_NAME = "camunda.debug.logging.enabled";
    public static final boolean DEBUG_LOGGING_ENABLED = Boolean.getBoolean(DEBUG_LOGGING_ENABLED_PROP_NAME);

    protected final WfDefinitionCache wfDefinitionCache;
    protected FlowElementVisitor flowElementVisitor = new FlowElementVisitor();
    protected IdGenerator idGenerator;

    protected final Int2ObjectHashMap<BpmnFlowElementAspectHandler> flowElementEventHandlers = new Int2ObjectHashMap<>();

    public FlowElementEventHandler(WfDefinitionCache wfDefinitionCache, IdGenerator idGenerator)
    {
        this.wfDefinitionCache = wfDefinitionCache;
        this.idGenerator = idGenerator;
    }

    @Override
    public void handle(BpmnFlowElementEventReader flowElementEventReader, ResponseControl responseControl, LogWriters logWriters)
    {
        final ProcessGraph process = wfDefinitionCache.getProcessGraphByTypeId(flowElementEventReader.wfDefinitionId());

        flowElementVisitor.init(process).moveToNode(flowElementEventReader.flowElementId());

        final BpmnAspect bpmnAspect = flowElementVisitor.aspectFor(flowElementEventReader.event());
        final BpmnFlowElementAspectHandler handler = flowElementEventHandlers.get(bpmnAspect.value());

        if (DEBUG_LOGGING_ENABLED)
        {
            System.out.println("Handling event of type " + flowElementEventReader.event());
        }

        handler.handle(flowElementEventReader, process, logWriters, idGenerator);
    }

    public void addAspectHandler(BpmnFlowElementAspectHandler aspectHandler)
    {
        flowElementEventHandlers.put(aspectHandler.getHandledBpmnAspect().value(), aspectHandler);
    }

}

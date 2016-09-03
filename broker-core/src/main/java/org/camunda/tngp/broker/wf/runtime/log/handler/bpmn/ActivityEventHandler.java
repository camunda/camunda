package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import org.agrona.collections.Int2ObjectHashMap;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.wf.repository.WfDefinitionCache;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class ActivityEventHandler implements LogEntryTypeHandler<BpmnActivityEventReader>
{
    public static final String DEBUG_LOGGING_ENABLED_PROP_NAME = "camunda.debug.logging.enabled";
    public static final boolean DEBUG_LOGGING_ENABLED = Boolean.getBoolean(DEBUG_LOGGING_ENABLED_PROP_NAME);

    protected final WfDefinitionCache wfDefinitionCache;
    protected FlowElementVisitor flowElementVisitor = new FlowElementVisitor();
    protected IdGenerator idGenerator;

    protected final Int2ObjectHashMap<BpmnActivityInstanceAspectHandler> activityEventHandlers = new Int2ObjectHashMap<>();

    public ActivityEventHandler(
            WfDefinitionCache wfDefinitionCache,
            IdGenerator idGenerator)
    {
        this.wfDefinitionCache = wfDefinitionCache;
        this.idGenerator = idGenerator;
    }

    @Override
    public void handle(BpmnActivityEventReader activityEventReader, ResponseControl responseControl, LogWriters logWriters)
    {
        final ProcessGraph process = wfDefinitionCache.getProcessGraphByTypeId(activityEventReader.wfDefinitionId());

        flowElementVisitor.init(process).moveToNode(activityEventReader.flowElementId());

        final BpmnAspect bpmnAspect = flowElementVisitor.aspectFor(activityEventReader.event());
        final BpmnActivityInstanceAspectHandler handler = activityEventHandlers.get(bpmnAspect.value());

        if (DEBUG_LOGGING_ENABLED)
        {
            System.out.println("Handling event of type " + activityEventReader.event());
        }

        handler.handle(activityEventReader, process, logWriters, idGenerator);
    }



    public void addAspectHandler(BpmnActivityInstanceAspectHandler aspectHandler)
    {
        activityEventHandlers.put(aspectHandler.getHandledBpmnAspect().value(), aspectHandler);
    }

}

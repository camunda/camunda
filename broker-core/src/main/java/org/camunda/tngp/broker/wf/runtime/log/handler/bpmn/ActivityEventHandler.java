package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.wf.repository.WfDefinitionCache;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;

public class ActivityEventHandler implements LogEntryTypeHandler<BpmnActivityEventReader>
{

    protected final WfDefinitionCache wfDefinitionCache;
    protected FlowElementVisitor flowElementVisitor = new FlowElementVisitor();
    protected LogWriter logWriter;
    protected IdGenerator idGenerator;

    protected final Int2ObjectHashMap<BpmnActivityInstanceAspectHandler> activityEventHandlers = new Int2ObjectHashMap<>();

    public ActivityEventHandler(
            WfDefinitionCache wfDefinitionCache,
            LogWriter logWriter,
            IdGenerator idGenerator)
    {
        this.wfDefinitionCache = wfDefinitionCache;
        this.logWriter = logWriter;
        this.idGenerator = idGenerator;
    }

    @Override
    public void handle(BpmnActivityEventReader activityEventReader, ResponseControl responseControl)
    {
        final ProcessGraph process = wfDefinitionCache.getProcessGraphByTypeId(activityEventReader.wfDefinitionId());

        flowElementVisitor.init(process).moveToNode(activityEventReader.flowElementId());

        final BpmnAspect bpmnAspect = flowElementVisitor.aspectFor(activityEventReader.event());
        final BpmnActivityInstanceAspectHandler handler = activityEventHandlers.get(bpmnAspect.value());

        System.out.println("Handling event of type " + activityEventReader.event());

        handler.handle(activityEventReader, process, logWriter, idGenerator);
    }



    public void addAspectHandler(BpmnActivityInstanceAspectHandler aspectHandler)
    {
        activityEventHandlers.put(aspectHandler.getHandledBpmnAspect().value(), aspectHandler);
    }

}

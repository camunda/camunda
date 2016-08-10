package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.wf.repository.WfDefinitionCache;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;

public class FlowElementEventHandler implements LogEntryTypeHandler<BpmnFlowElementEventReader>
{

    protected final WfDefinitionCache wfDefinitionCache;
    protected FlowElementVisitor flowElementVisitor = new FlowElementVisitor();
    protected LogWriter logWriter;
    protected IdGenerator idGenerator;

    protected final Int2ObjectHashMap<BpmnFlowElementAspectHandler> flowElementEventHandlers = new Int2ObjectHashMap<>();

    public FlowElementEventHandler(WfDefinitionCache wfDefinitionCache, LogWriter logWriter, IdGenerator idGenerator)
    {
        this.wfDefinitionCache = wfDefinitionCache;
        this.logWriter = logWriter;
        this.idGenerator = idGenerator;
    }

    @Override
    public void handle(BpmnFlowElementEventReader flowElementEventReader, ResponseControl responseControl)
    {
        final ProcessGraph process = wfDefinitionCache.getProcessGraphByTypeId(flowElementEventReader.wfDefinitionId());

        flowElementVisitor.init(process).moveToNode(flowElementEventReader.flowElementId());

        final BpmnAspect bpmnAspect = flowElementVisitor.aspectFor(flowElementEventReader.event());
        final BpmnFlowElementAspectHandler handler = flowElementEventHandlers.get(bpmnAspect.value());

        System.out.println("Handling event of type " + flowElementEventReader.event());

        handler.handle(flowElementEventReader, process, logWriter, idGenerator);
    }

    public void addAspectHandler(BpmnFlowElementAspectHandler aspectHandler)
    {
        flowElementEventHandlers.put(aspectHandler.getHandledBpmnAspect().value(), aspectHandler);
    }

}

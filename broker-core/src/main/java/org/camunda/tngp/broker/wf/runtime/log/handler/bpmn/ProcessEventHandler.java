package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.wf.repository.WfDefinitionCache;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;

public class ProcessEventHandler implements LogEntryTypeHandler<BpmnProcessEventReader>
{

    protected final WfDefinitionCache wfDefinitionCache;
    protected FlowElementVisitor flowElementVisitor = new FlowElementVisitor();
    protected LogWriter logWriter;
    protected IdGenerator idGenerator;

    protected final Int2ObjectHashMap<BpmnProcessAspectHandler> processEventHandlers = new Int2ObjectHashMap<>();

    public ProcessEventHandler(
            WfDefinitionCache wfDefinitionCache,
            LogWriter logWriter,
            IdGenerator idGenerator)
    {
        this.wfDefinitionCache = wfDefinitionCache;
        this.logWriter = logWriter;
        this.idGenerator = idGenerator;
    }

    @Override
    public void handle(BpmnProcessEventReader processEventReader, ResponseControl responseControl)
    {
        final ProcessGraph process = wfDefinitionCache.getProcessGraphByTypeId(processEventReader.processId());

        // TODO: move to process node; how to find it? Do we need to store the node id (!= process id) in the process event?
        flowElementVisitor.init(process).moveToNode(0);

        final BpmnAspect bpmnAspect = flowElementVisitor.aspectFor(processEventReader.event());
        final BpmnProcessAspectHandler handler = processEventHandlers.get(bpmnAspect.value());

        System.out.println("Handling event of type " + processEventReader.event());

        handler.handle(processEventReader, process, logWriter, idGenerator);

    }

    public void addAspectHandler(BpmnProcessAspectHandler aspectHandler)
    {
        processEventHandlers.put(aspectHandler.getHandledBpmnAspect().value(), aspectHandler);
    }

}

package org.camunda.tngp.broker.wf.runtime.operation;

import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.log.ExecutionLog;
import org.camunda.tngp.broker.wf.runtime.log.ExecutionLogEntry;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class CreateProcessInstanceOperation
{
    protected final FlowElementVisitor flowElementVisitor = new FlowElementVisitor();

    protected final ExecutionLogEntry procInstCreatedEvt = new ExecutionLogEntry();
    protected final ExecutionLogEntry initialFlowNodeCreatedEvt = new ExecutionLogEntry();
    protected final ExecutionLogEntry[] batch = new ExecutionLogEntry[] { procInstCreatedEvt, initialFlowNodeCreatedEvt };

    protected final IdGenerator idGenerator;
    protected final ExecutionLog executionLog;

    public CreateProcessInstanceOperation(WfRuntimeContext bpmnExecutorContext)
    {
        idGenerator = bpmnExecutorContext.getIdGenerator();
        executionLog = bpmnExecutorContext.getExecutionLog();
    }

    public long execute(final ProcessGraph processGraph, final long processInstanceId)
    {
        final long initialFlowNodeInstanceId = idGenerator.nextId();

        final long processId = processGraph.id();
        final int intialFlowNodeId = processGraph.intialFlowNodeId();

        flowElementVisitor
            .init(processGraph)
            .moveToNode(0);

        procInstCreatedEvt
            .key(processInstanceId)
            .event(flowElementVisitor.onEnterEvent())
            .processId(processId)
            .processInstanceId(processInstanceId)
            .parentFlowElementInstanceId(-1)
            .flowElementId(0)
            .flowElementType(flowElementVisitor.type());

        flowElementVisitor.moveToNode(intialFlowNodeId);

        initialFlowNodeCreatedEvt
            .key(initialFlowNodeInstanceId)
            .event(flowElementVisitor.onEnterEvent())
            .processId(processId)
            .processInstanceId(processInstanceId)
            .parentFlowElementInstanceId(processInstanceId)
            .flowElementId(intialFlowNodeId)
            .flowElementType(flowElementVisitor.type());

        return executionLog.writeBatch(batch);
    }

}

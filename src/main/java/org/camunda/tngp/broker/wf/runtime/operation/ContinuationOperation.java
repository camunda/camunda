package org.camunda.tngp.broker.wf.runtime.operation;

import org.camunda.tngp.bpmn.graph.BpmnEdgeTypes;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.log.ExecutionLog;
import org.camunda.tngp.broker.wf.runtime.log.ExecutionLogEntry;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class ContinuationOperation
{
    protected final FlowElementVisitor graphNavigator = new FlowElementVisitor();

    protected final ExecutionLogEntry sequenceFlowLogEntry = new ExecutionLogEntry();
    protected final ExecutionLogEntry nextNodeLogEntry = new ExecutionLogEntry();
    protected final ExecutionLogEntry[] followSequenceFlowBatch = new ExecutionLogEntry[] { sequenceFlowLogEntry, nextNodeLogEntry };

    protected final ExecutionLogEntry parentLogEntry = new ExecutionLogEntry();

    protected final IdGenerator idGenerator;
    protected final ExecutionLog executionLog;

    public ContinuationOperation(WfRuntimeContext context)
    {
        idGenerator = context.getIdGenerator();
        executionLog = context.getExecutionLog();
    }

    public long execute(final ExecutionLogEntry continuationEvent, final ProcessGraph processGraph)
    {
        graphNavigator
            .init(processGraph)
            .moveToNode(continuationEvent.flowElementId());

        long logOffset = 0;

        if(graphNavigator.hasOutgoingSequenceFlows())
        {
            // TODO: implement conditions

            graphNavigator.traverseEdge(BpmnEdgeTypes.NODE_OUTGOING_SEQUENCE_FLOWS);

            sequenceFlowLogEntry.key(idGenerator.nextId())
                .event(graphNavigator.onEnterEvent())
                .processId(processGraph.id())
                .processInstanceId(continuationEvent.processInstanceId())
                .parentFlowElementInstanceId(continuationEvent.parentFlowElementInstanceId())
                .flowElementId(graphNavigator.nodeId())
                .flowElementType(graphNavigator.type());

            graphNavigator.traverseEdge(BpmnEdgeTypes.SEQUENCE_FLOW_TARGET_NODE);

            nextNodeLogEntry.key(idGenerator.nextId())
                .event(graphNavigator.onEnterEvent())
                .processId(processGraph.id())
                .processInstanceId(continuationEvent.processInstanceId())
                .parentFlowElementInstanceId(continuationEvent.parentFlowElementInstanceId())
                .flowElementId(graphNavigator.nodeId())
                .flowElementType(graphNavigator.type());

            logOffset = executionLog.writeBatch(followSequenceFlowBatch);
        }
        else if(continuationEvent.parentFlowElementInstanceId() != -1)
        {
            executionLog.read(continuationEvent.parentFlowElementInstanceId(), parentLogEntry);

            graphNavigator.moveToNode(parentLogEntry.flowElementId());

            // TODO: implement join

            parentLogEntry.key(idGenerator.nextId())
                .event(graphNavigator.onLeaveEvent());

            logOffset = executionLog.write(parentLogEntry);
        }

        return logOffset;
    }
}

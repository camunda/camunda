package org.camunda.tngp.bpmn.executor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.tngp.bpmn.graph.BpmnEdgeTypes;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.bpmn.graph.transformer.BpmnModelInstanceTransformer;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.log.ExecutionLogEntry;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.graph.bpmn.FlowElementType;
import org.junit.Before;
import org.junit.Test;

public class ExecuteContinuationTest
{
    FlowElementVisitor flowElementVisitor;

    BpmnExecutor executor;
    TestIdGenerator idGenerator;

    TestExecutionLog log;
    BpmnModelInstanceTransformer processGraphTransformer;

    @Before
    public void setUp()
    {
        flowElementVisitor = new FlowElementVisitor();

        idGenerator = new TestIdGenerator();
        log = new TestExecutionLog();
        processGraphTransformer = new BpmnModelInstanceTransformer();

        final WfRuntimeContext bpmnExecutorContext = new WfRuntimeContext();
        bpmnExecutorContext.setBpmnIdGenerator(idGenerator);
        bpmnExecutorContext.setExecutionLog(log);

        executor = new BpmnExecutor(bpmnExecutorContext);
    }

    @Test
    public void shouldCreateProcInst()
    {
        // given
        final ProcessGraph processGraph = processGraphTransformer.transformSingleProcess(Bpmn.createExecutableProcess()
                .startEvent()
                .endEvent()
                .done());

        executor.createProcessInstanceAtInitial(processGraph, 0);

        // if
        executor.executeContinuation(processGraph, log.getLoggedEvents().get(1));

        // then
        final List<ExecutionLogEntry> loggedEvents = log.getLoggedEvents();
        assertThat(loggedEvents.size()).isEqualTo(4);
        flowElementVisitor.init(processGraph);

        ExecutionLogEntry processInstanceEvt = loggedEvents.get(0);

        ExecutionLogEntry event1 = loggedEvents.get(2);
        flowElementVisitor.moveToNode(processGraph.intialFlowNodeId()).traverseEdge(BpmnEdgeTypes.NODE_OUTGOING_SEQUENCE_FLOWS);
        assertThat(event1.event()).isEqualTo(ExecutionEventType.SQF_EXECUTED);
        assertThat(event1.flowElementId()).isEqualTo(flowElementVisitor.nodeId());
        assertThat(event1.flowElementType()).isEqualTo(FlowElementType.SEQUENCE_FLOW);
        assertThat(event1.parentFlowElementInstanceId()).isEqualTo(processInstanceEvt.key());
        assertThat(event1.processId()).isEqualTo(processGraph.id());
        assertThat(event1.processInstanceId()).isEqualTo(processInstanceEvt.key());

        ExecutionLogEntry event2 = loggedEvents.get(3);
        flowElementVisitor.traverseEdge(BpmnEdgeTypes.SEQUENCE_FLOW_TARGET_NODE);
        assertThat(event2.event()).isEqualTo(ExecutionEventType.EVT_OCCURRED);
        assertThat(event2.flowElementId()).isEqualTo(flowElementVisitor.nodeId());
        assertThat(event2.flowElementType()).isEqualTo(FlowElementType.END_EVENT);
        assertThat(event2.parentFlowElementInstanceId()).isEqualTo(processInstanceEvt.key());
        assertThat(event2.processId()).isEqualTo(processGraph.id());
        assertThat(event2.processInstanceId()).isEqualTo(processInstanceEvt.key());
    }

    @Test
    public void shouldCompleteProcInst()
    {
        // given
        final ProcessGraph processGraph = processGraphTransformer.transformSingleProcess(Bpmn.createExecutableProcess()
                .startEvent()
                .endEvent()
                .done());

        executor.createProcessInstanceAtInitial(processGraph, 0);
        executor.executeContinuation(processGraph, log.getLastEvent());

        // if
        executor.executeContinuation(processGraph, log.getLastEvent());

        // then
        final List<ExecutionLogEntry> loggedEvents = log.getLoggedEvents();
        assertThat(loggedEvents.size()).isEqualTo(5);
        flowElementVisitor.init(processGraph)
            .moveToNode(0);

        ExecutionLogEntry processInstanceEvt = loggedEvents.get(0);

        ExecutionLogEntry event1 = log.getLastEvent();
        assertThat(event1.event()).isEqualTo(ExecutionEventType.PROC_INST_COMPLETED);
        assertThat(event1.flowElementId()).isEqualTo(flowElementVisitor.nodeId());
        assertThat(event1.flowElementType()).isEqualTo(FlowElementType.PROCESS);
        assertThat(event1.parentFlowElementInstanceId()).isEqualTo(-1);
        assertThat(event1.processId()).isEqualTo(processGraph.id());
        assertThat(event1.processInstanceId()).isEqualTo(processInstanceEvt.key());

    }
}

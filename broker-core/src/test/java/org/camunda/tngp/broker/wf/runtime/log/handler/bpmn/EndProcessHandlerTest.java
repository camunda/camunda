package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.bpmn.TngpModelInstance.wrap;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.bpmn.graph.BpmnEdgeTypes;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.bpmn.graph.transformer.BpmnModelInstanceTransformer;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.broker.util.mocks.StubLogWriters;
import org.camunda.tngp.broker.util.mocks.WfRuntimeEvents;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EndProcessHandlerTest
{

    protected StubLogReader logReader;
    protected StubLogWriter logWriter;
    protected StubLogWriters logWriters;

    @Mock
    protected Long2LongHashIndex workflowEventIndex;

    protected IdGenerator idGenerator;

    protected ProcessGraph endEventProcess;
    protected ProcessGraph serviceTaskProcess;
    protected FlowElementVisitor elementVisitor;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        idGenerator = new PrivateIdGenerator(0);

        logReader = new StubLogReader(null);
        logWriter = new StubLogWriter();
        logWriters = new StubLogWriters(0, logWriter);
    }

    @Before
    public void createProcess()
    {
        final BpmnModelInstance serviceTaskModel = Bpmn.createExecutableProcess("process")
                .startEvent("startEvent")
                .serviceTask("serviceTask")
                .done();

        wrap(serviceTaskModel).taskAttributes("serviceTask", "foO", 6);

        serviceTaskProcess = new BpmnModelInstanceTransformer().transformSingleProcess(serviceTaskModel, 0L);

        final BpmnModelInstance endEventModel = Bpmn.createExecutableProcess("process")
                .startEvent("startEvent")
                .endEvent("endEvent")
                .done();

        endEventProcess = new BpmnModelInstanceTransformer().transformSingleProcess(endEventModel, 0L);

        elementVisitor = new FlowElementVisitor();
    }

    @Test
    public void shouldHandleFlowElementEvent()
    {
        // given
        elementVisitor.init(endEventProcess).moveToNode(endEventProcess.intialFlowNodeId());
        final int endEventId = elementVisitor
            .traverseEdge(BpmnEdgeTypes.NODE_OUTGOING_SEQUENCE_FLOWS)
            .traverseEdge(BpmnEdgeTypes.SEQUENCE_FLOW_TARGET_NODE)
            .nodeId();

        final BpmnFlowElementEventReader flowEventReader = WfRuntimeEvents.mockFlowElementEvent();
        when(flowEventReader.event()).thenReturn(ExecutionEventType.EVT_OCCURRED);
        when(flowEventReader.flowElementId()).thenReturn(endEventId);

        logReader.addEntry(WfRuntimeEvents.createProcessEvent(ExecutionEventType.PROC_INST_CREATED));
        when(workflowEventIndex.get(eq(WfRuntimeEvents.PROCESS_INSTANCE_ID), anyLong())).thenReturn(logReader.getEntryPosition(0));

        final EndProcessHandler eventHandler = new EndProcessHandler(logReader, workflowEventIndex);

        // when
        eventHandler.handle(flowEventReader, endEventProcess, logWriters, idGenerator);

        // then
        assertThat(logWriters.writtenEntries()).isEqualTo(1);
        assertThat(logWriter.size()).isEqualTo(1);

        final BpmnProcessEventReader newEvent = logWriter.getEntryAs(0, BpmnProcessEventReader.class);
        assertThat(newEvent.event()).isEqualTo(ExecutionEventType.PROC_INST_COMPLETED);
        assertThat(newEvent.processId()).isEqualTo(WfRuntimeEvents.PROCESS_ID);
        assertThat(newEvent.processInstanceId()).isEqualTo(WfRuntimeEvents.PROCESS_INSTANCE_ID);
        assertThat(newEvent.initialElementId()).isEqualTo(WfRuntimeEvents.FLOW_ELEMENT_ID);
        assertThat(newEvent.key()).isEqualTo(WfRuntimeEvents.PROCESS_INSTANCE_ID);
    }

    @Test
    public void shouldHandleActivityInstanceEvent()
    {
        // given
        elementVisitor.init(serviceTaskProcess).moveToNode(serviceTaskProcess.intialFlowNodeId());
        final int serviceTaskId = elementVisitor
            .traverseEdge(BpmnEdgeTypes.NODE_OUTGOING_SEQUENCE_FLOWS)
            .traverseEdge(BpmnEdgeTypes.SEQUENCE_FLOW_TARGET_NODE)
            .nodeId();

        final BpmnActivityEventReader activityInstanceEvent = WfRuntimeEvents.mockActivityInstanceEvent(ExecutionEventType.EVT_OCCURRED);
        when(activityInstanceEvent.flowElementId()).thenReturn(serviceTaskId);

        logReader.addEntry(WfRuntimeEvents.createProcessEvent(ExecutionEventType.PROC_INST_CREATED));
        when(workflowEventIndex.get(eq(WfRuntimeEvents.PROCESS_INSTANCE_ID), anyLong())).thenReturn(logReader.getEntryPosition(0));

        final EndProcessHandler eventHandler = new EndProcessHandler(logReader, workflowEventIndex);

        // when
        eventHandler.handle(activityInstanceEvent, serviceTaskProcess, logWriters, idGenerator);

        // then
        assertThat(logWriters.writtenEntries()).isEqualTo(1);
        assertThat(logWriter.size()).isEqualTo(1);

        final BpmnProcessEventReader newEvent = logWriter.getEntryAs(0, BpmnProcessEventReader.class);
        assertThat(newEvent.event()).isEqualTo(ExecutionEventType.PROC_INST_COMPLETED);
        assertThat(newEvent.processId()).isEqualTo(WfRuntimeEvents.PROCESS_ID);
        assertThat(newEvent.processInstanceId()).isEqualTo(WfRuntimeEvents.PROCESS_INSTANCE_ID);
        assertThat(newEvent.initialElementId()).isEqualTo(WfRuntimeEvents.FLOW_ELEMENT_ID);
        assertThat(newEvent.key()).isEqualTo(WfRuntimeEvents.PROCESS_INSTANCE_ID);
    }

    @Test
    public void shouldHandleTriggerNoneEventAspect()
    {
        // given
        final EndProcessHandler handler = new EndProcessHandler(logReader, workflowEventIndex);

        // when
        final BpmnAspect bpmnAspect = handler.getHandledBpmnAspect();

        // then
        assertThat(bpmnAspect).isEqualTo(BpmnAspect.END_PROCESS);
    }
}

package org.camunda.tngp.broker.wf.runtime.bpmn.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.bpmn.TngpModelInstance.wrap;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.bpmn.graph.BpmnEdgeTypes;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.bpmn.graph.transformer.BpmnModelInstanceTransformer;
import org.camunda.tngp.broker.test.util.BufferWriterMatcher;
import org.camunda.tngp.broker.util.mocks.BpmnEventMocks;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class EndProcessHandlerTest
{

    @Mock
    protected BpmnProcessEventReader processEventReader;

    @Mock
    protected BpmnFlowElementEventReader flowEventReader;

    @Mock
    protected BpmnActivityEventReader activityEventReader;

    @Mock
    protected LogReader logReader;

    @Mock
    protected Long2LongHashIndex workflowEventIndex;

    @Mock
    protected LogWriter logWriter;

    protected IdGenerator idGenerator;

    protected ProcessGraph endEventProcess;
    protected ProcessGraph serviceTaskProcess;
    protected FlowElementVisitor elementVisitor;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        idGenerator = new PrivateIdGenerator(0);
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

        BpmnEventMocks.mockFlowElementEvent(null, flowEventReader);
        when(flowEventReader.event()).thenReturn(ExecutionEventType.EVT_OCCURRED);
        when(flowEventReader.flowElementId()).thenReturn(endEventId);

        BpmnEventMocks.mockProcessEvent(null, processEventReader);

        when(workflowEventIndex.get(eq(BpmnEventMocks.PROCESS_INSTANCE_ID), anyLong(), anyLong())).thenReturn(748L);

        final EndProcessHandler eventHandler = new EndProcessHandler(logReader, workflowEventIndex);
        eventHandler.setLatestEventReader(processEventReader);

        // when
        eventHandler.handle(flowEventReader, endEventProcess, logWriter, idGenerator);

        // then
        final InOrder inOrder = Mockito.inOrder(logWriter, logReader);
        inOrder.verify(logReader).setPosition(748L);
        inOrder.verify(logReader).read(processEventReader);
        inOrder.verify(logWriter).write(argThat(BufferWriterMatcher.writesProperties(BpmnProcessEventReader.class)
                .matching((r) -> r.event(), ExecutionEventType.PROC_INST_COMPLETED)
                .matching((r) -> r.processId(), BpmnEventMocks.PROCESS_ID)
                .matching((r) -> r.processInstanceId(), BpmnEventMocks.PROCESS_INSTANCE_ID)
                .matching((r) -> r.initialElementId(), BpmnEventMocks.FLOW_ELEMENT_ID)
                .matching((r) -> r.key(), BpmnEventMocks.KEY)
            ));

        verifyNoMoreInteractions(logWriter, logReader);
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

        BpmnEventMocks.mockActivityInstanceEvent(activityEventReader, ExecutionEventType.EVT_OCCURRED);
        when(activityEventReader.flowElementId()).thenReturn(serviceTaskId);

        BpmnEventMocks.mockProcessEvent(null, processEventReader);

        when(workflowEventIndex.get(eq(BpmnEventMocks.PROCESS_INSTANCE_ID), anyLong(), anyLong())).thenReturn(748L);

        final EndProcessHandler eventHandler = new EndProcessHandler(logReader, workflowEventIndex);
        eventHandler.setLatestEventReader(processEventReader);

        // when
        eventHandler.handle(activityEventReader, serviceTaskProcess, logWriter, idGenerator);

        // then
        final InOrder inOrder = Mockito.inOrder(logWriter, logReader);
        inOrder.verify(logReader).setPosition(748L);
        inOrder.verify(logReader).read(processEventReader);

        inOrder.verify(logWriter).write(argThat(BufferWriterMatcher.writesProperties(BpmnProcessEventReader.class)
                .matching((r) -> r.event(), ExecutionEventType.PROC_INST_COMPLETED)
                .matching((r) -> r.processId(), BpmnEventMocks.PROCESS_ID)
                .matching((r) -> r.processInstanceId(), BpmnEventMocks.PROCESS_INSTANCE_ID)
                .matching((r) -> r.initialElementId(), BpmnEventMocks.FLOW_ELEMENT_ID)
                .matching((r) -> r.key(), BpmnEventMocks.KEY)
            ));

        verifyNoMoreInteractions(logWriter, logReader);
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

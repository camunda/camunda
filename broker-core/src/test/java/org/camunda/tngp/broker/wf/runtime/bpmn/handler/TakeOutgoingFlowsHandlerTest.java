package org.camunda.tngp.broker.wf.runtime.bpmn.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.bpmn.TngpModelInstance.wrap;
import static org.hamcrest.Matchers.not;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.longThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.bpmn.graph.BpmnEdgeTypes;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.bpmn.graph.transformer.BpmnModelInstanceTransformer;
import org.camunda.tngp.broker.test.util.FluentMock;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventWriter;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TakeOutgoingFlowsHandlerTest
{

    @FluentMock
    protected BpmnFlowElementEventWriter eventWriter;

    @Mock
    protected BpmnProcessEventReader processEventReader;

    @Mock
    protected BpmnActivityEventReader activityEventReader;

    @Mock
    protected LogWriter logWriter;

    protected ProcessGraph process;
    protected FlowElementVisitor elementVisitor;

    protected IdGenerator idGenerator;

    @Before
    public void initMocks()
    {
        MockitoAnnotations.initMocks(this);
        idGenerator = new PrivateIdGenerator(0);
    }

    @Before
    public void createProcess()
    {
        final BpmnModelInstance model = Bpmn.createExecutableProcess("process")
                .startEvent("startEvent")
                .serviceTask("serviceTask")
                .endEvent("endEvent")
                .done();

        wrap(model).taskAttributes("serviceTask", "foo", 4);

        process = new BpmnModelInstanceTransformer().transformSingleProcess(model, 0L);
        elementVisitor = new FlowElementVisitor();
    }

    @Test
    public void testWriteProcessInitialSequenceFlowEvent()
    {
        // given
        when(processEventReader.event()).thenReturn(ExecutionEventType.PROC_INST_CREATED);
        when(processEventReader.initialElementId()).thenReturn(process.intialFlowNodeId());
        when(processEventReader.key()).thenReturn(1234L);
        when(processEventReader.processId()).thenReturn(467L);
        when(processEventReader.processInstanceId()).thenReturn(9876L);

        final TakeOutgoingFlowsHandler handler = new TakeOutgoingFlowsHandler();
        handler.setEventWriter(eventWriter);

        // when
        handler.handle(processEventReader, process, logWriter, idGenerator);

        // then
        elementVisitor.init(process).moveToNode(process.intialFlowNodeId());
        final int sequenceFlowId = elementVisitor.traverseEdge(BpmnEdgeTypes.NODE_OUTGOING_SEQUENCE_FLOWS).nodeId();

        verify(eventWriter).eventType(ExecutionEventType.SQF_EXECUTED);
        verify(eventWriter).flowElementId(sequenceFlowId);
        verify(eventWriter).key(anyLong());
        verify(eventWriter).processId(467L);
        verify(eventWriter).workflowInstanceId(9876L);

        verify(logWriter).write(eventWriter);
    }

    @Test
    public void shouldWriteActivityOutgoingSequenceFlowEvent()
    {
        // given
        final int serviceTaskId = elementVisitor
            .init(process)
            .moveToNode(process.intialFlowNodeId())
            .traverseSingleOutgoingSequenceFlow()
            .nodeId();

        final int sequenceFlowId = elementVisitor
            .traverseEdge(BpmnEdgeTypes.NODE_OUTGOING_SEQUENCE_FLOWS)
            .nodeId();

        when(activityEventReader.flowElementId()).thenReturn(serviceTaskId);
        when(activityEventReader.event()).thenReturn(ExecutionEventType.ACT_INST_COMPLETED);
        // TODO: mock task type
//        when(activityEventReader.getTaskType())
        when(activityEventReader.key()).thenReturn(764L);
        when(activityEventReader.wfDefinitionId()).thenReturn(9876L);
        when(activityEventReader.wfInstanceId()).thenReturn(893L);
        when(activityEventReader.resourceId()).thenReturn(78);
        when(activityEventReader.taskQueueId()).thenReturn(4);

        final TakeOutgoingFlowsHandler handler = new TakeOutgoingFlowsHandler();
        handler.setEventWriter(eventWriter);

        // when
        handler.handle(activityEventReader, process, logWriter, idGenerator);

        // then
        final InOrder inOrder = inOrder(eventWriter, logWriter);

        inOrder.verify(eventWriter).eventType(ExecutionEventType.SQF_EXECUTED);
        inOrder.verify(eventWriter).flowElementId(sequenceFlowId);
        inOrder.verify(eventWriter).key(longThat(not(764L)));
        inOrder.verify(eventWriter).processId(9876L);
        inOrder.verify(eventWriter).workflowInstanceId(893L);
        inOrder.verify(logWriter).write(eventWriter);
    }

    @Test
    public void shouldHandleTakeOutgoingFlowsAspect()
    {
        // given
        final TakeOutgoingFlowsHandler handler = new TakeOutgoingFlowsHandler();

        // when
        final BpmnAspect handledBpmnAspect = handler.getHandledBpmnAspect();

        // then
        assertThat(handledBpmnAspect).isEqualTo(BpmnAspect.TAKE_OUTGOING_FLOWS);
    }

    // TODO: test when log cannot be written
}

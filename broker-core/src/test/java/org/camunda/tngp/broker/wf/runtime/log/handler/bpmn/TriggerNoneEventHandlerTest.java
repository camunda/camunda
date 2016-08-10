package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.mockito.Matchers.longThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.bpmn.graph.BpmnEdgeTypes;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.bpmn.graph.transformer.BpmnModelInstanceTransformer;
import org.camunda.tngp.broker.test.util.FluentMock;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventWriter;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class TriggerNoneEventHandlerTest
{

    @FluentMock
    protected BpmnFlowElementEventWriter flowEventWriter;

    @Mock
    protected BpmnFlowElementEventReader flowEventReader;

    @Mock
    protected LogWriter logWriter;

    protected IdGenerator idGenerator;

    protected ProcessGraph process;
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
        final BpmnModelInstance model = Bpmn.createExecutableProcess("process")
                .startEvent("startEvent")
                .sequenceFlowId("sequenceFlow")
                .endEvent("endEvent")
                .done();

        process = new BpmnModelInstanceTransformer().transformSingleProcess(model, 0L);
        elementVisitor = new FlowElementVisitor();
    }

    @Test
    public void shouldHandleSequenceFlowEvent()
    {
        // given
        elementVisitor.init(process).moveToNode(process.intialFlowNodeId());
        final int sequenceFlowId = elementVisitor.traverseEdge(BpmnEdgeTypes.NODE_OUTGOING_SEQUENCE_FLOWS).nodeId();
        final int eventId = elementVisitor.traverseEdge(BpmnEdgeTypes.SEQUENCE_FLOW_TARGET_NODE).nodeId();

        when(flowEventReader.event()).thenReturn(ExecutionEventType.SQF_EXECUTED);
        when(flowEventReader.flowElementId()).thenReturn(sequenceFlowId);
        when(flowEventReader.key()).thenReturn(1234L);
        when(flowEventReader.wfDefinitionId()).thenReturn(467L);
        when(flowEventReader.wfInstanceId()).thenReturn(9876L);

        final TriggerNoneEventHandler eventHandler = new TriggerNoneEventHandler();
        eventHandler.setEventWriter(flowEventWriter);

        // when
        eventHandler.handle(flowEventReader, process, logWriter, idGenerator);

        // then
        final InOrder inOrder = Mockito.inOrder(flowEventWriter, logWriter);
        inOrder.verify(flowEventWriter).eventType(ExecutionEventType.EVT_OCCURRED);
        inOrder.verify(flowEventWriter).flowElementId(eventId);
        inOrder.verify(flowEventWriter).key(longThat(not(1234L)));
        inOrder.verify(flowEventWriter).processId(467L);
        inOrder.verify(flowEventWriter).workflowInstanceId(9876L);
        inOrder.verify(logWriter).write(flowEventWriter);

        verifyNoMoreInteractions(logWriter, flowEventWriter);
    }

    @Test
    public void shouldHandleTriggerNoneEventAspect()
    {
        // given
        final TriggerNoneEventHandler handler = new TriggerNoneEventHandler();

        // when
        final BpmnAspect bpmnAspect = handler.getHandledBpmnAspect();

        // then
        assertThat(bpmnAspect).isEqualTo(BpmnAspect.TRIGGER_NONE_EVENT);
    }

}

package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.bpmn.graph.BpmnEdgeTypes;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.bpmn.graph.transformer.BpmnModelInstanceTransformer;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.broker.util.mocks.StubLogWriters;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TriggerNoneEventHandlerTest
{

    @Mock
    protected BpmnFlowElementEventReader flowEventReader;

    protected StubLogWriter logWriter;
    protected StubLogWriters logWriters;

    protected IdGenerator idGenerator;

    protected ProcessGraph process;
    protected FlowElementVisitor elementVisitor;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        idGenerator = new PrivateIdGenerator(0);

        logWriter = new StubLogWriter();
        logWriters = new StubLogWriters(0, logWriter);
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

        // when
        eventHandler.handle(flowEventReader, process, logWriters, idGenerator);

        // then
        assertThat(logWriters.writtenEntries()).isEqualTo(1);
        final BpmnFlowElementEventReader reader = logWriter.getEntryAs(0, BpmnFlowElementEventReader.class);

        assertThat(reader.event()).isEqualTo(ExecutionEventType.EVT_OCCURRED);
        assertThat(reader.flowElementId()).isEqualTo(eventId);
        assertThat(reader.key()).isGreaterThanOrEqualTo(0L);
        assertThat(reader.wfDefinitionId()).isEqualTo(467L);
        assertThat(reader.wfInstanceId()).isEqualTo(9876L);
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

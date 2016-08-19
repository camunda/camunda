package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.bpmn.TngpModelInstance.wrap;
import static org.mockito.Mockito.when;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.bpmn.graph.BpmnEdgeTypes;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.bpmn.graph.transformer.BpmnModelInstanceTransformer;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.broker.util.mocks.StubLogWriters;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TakeOutgoingFlowsHandlerTest
{

    @Mock
    protected BpmnProcessEventReader processEventReader;

    @Mock
    protected BpmnActivityEventReader activityEventReader;

    protected StubLogWriter logWriter;
    protected StubLogWriters logWriters;

    protected ProcessGraph process;
    protected FlowElementVisitor elementVisitor;

    protected IdGenerator idGenerator;

    @Before
    public void initMocks()
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

        // when
        handler.handle(processEventReader, process, logWriters, idGenerator);

        // then
        elementVisitor.init(process).moveToNode(process.intialFlowNodeId());
        final int sequenceFlowId = elementVisitor.traverseEdge(BpmnEdgeTypes.NODE_OUTGOING_SEQUENCE_FLOWS).nodeId();

        assertThat(logWriters.writtenEntries()).isEqualTo(1);
        final BpmnFlowElementEventReader reader = logWriter.getEntryAs(0, BpmnFlowElementEventReader.class);

        assertThat(reader.event()).isEqualTo(ExecutionEventType.SQF_EXECUTED);
        assertThat(reader.flowElementId()).isEqualTo(sequenceFlowId);
        assertThat(reader.key()).isGreaterThanOrEqualTo(0L);
        assertThat(reader.wfDefinitionId()).isEqualTo(467L);
        assertThat(reader.wfInstanceId()).isEqualTo(9876L);
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

        // when
        handler.handle(activityEventReader, process, logWriters, idGenerator);

        // then
        assertThat(logWriters.writtenEntries()).isEqualTo(1);
        final BpmnFlowElementEventReader reader = logWriter.getEntryAs(0, BpmnFlowElementEventReader.class);

        assertThat(reader.event()).isEqualTo(ExecutionEventType.SQF_EXECUTED);
        assertThat(reader.flowElementId()).isEqualTo(sequenceFlowId);
        assertThat(reader.key()).isGreaterThanOrEqualTo(0L);
        assertThat(reader.wfDefinitionId()).isEqualTo(9876L);
        assertThat(reader.wfInstanceId()).isEqualTo(893L);
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

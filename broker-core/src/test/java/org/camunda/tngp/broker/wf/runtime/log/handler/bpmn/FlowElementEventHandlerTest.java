package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.bpmn.graph.transformer.BpmnModelInstanceTransformer;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class FlowElementEventHandlerTest
{
    @Mock
    protected WfDefinitionCache wfDefinitionCache;

    @Mock
    protected LogWriters logWriters;

    @Mock
    protected IdGenerator idGenerator;

    @Mock
    protected ResponseControl responseControl;

    protected ProcessGraph process;
    protected int endEventId;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        final BpmnModelInstance model = Bpmn.createExecutableProcess("process")
                .startEvent("startEvent")
                .endEvent("endEvent")
                .done();

        process = new BpmnModelInstanceTransformer().transformSingleProcess(model, 0L);
        when(wfDefinitionCache.getProcessGraphByTypeId(process.id())).thenReturn(process);

        final FlowElementVisitor visitor = new FlowElementVisitor();
        endEventId = visitor
                .init(process)
                .moveToNode(process.intialFlowNodeId())
                .traverseSingleOutgoingSequenceFlow()
                .nodeId();
    }

    @Test
    public void shouldDelegateToRegisteredBpmnAspectHandler()
    {
        // given
        final FlowElementEventHandler handler = new FlowElementEventHandler(wfDefinitionCache, idGenerator);

        final BpmnFlowElementAspectHandler aspectHandler = mock(BpmnFlowElementAspectHandler.class);
        when(aspectHandler.getHandledBpmnAspect()).thenReturn(BpmnAspect.END_PROCESS);

        handler.addAspectHandler(aspectHandler);

        final BpmnFlowElementEventReader eventReader = mock(BpmnFlowElementEventReader.class);
        when(eventReader.event()).thenReturn(ExecutionEventType.EVT_OCCURRED);
        when(eventReader.flowElementId()).thenReturn(endEventId);
        when(eventReader.wfDefinitionId()).thenReturn(process.id());
        when(eventReader.wfInstanceId()).thenReturn(123L);

        // when
        handler.handle(eventReader, responseControl, logWriters);

        // then
        verify(aspectHandler, times(1)).handle(eventReader, process, logWriters, idGenerator);
        verifyZeroInteractions(logWriters);
    }
}

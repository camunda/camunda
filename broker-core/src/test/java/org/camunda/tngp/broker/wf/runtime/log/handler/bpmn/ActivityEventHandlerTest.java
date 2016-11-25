package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import static org.camunda.tngp.broker.test.util.bpmn.TngpModelInstance.wrap;
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
import org.camunda.tngp.broker.logstreams.LogWriters;
import org.camunda.tngp.broker.logstreams.ResponseControl;
import org.camunda.tngp.broker.wf.runtime.WfDefinitionCache;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ActivityEventHandlerTest
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
    protected int serviceTaskId;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        final BpmnModelInstance model = Bpmn.createExecutableProcess("process")
                .startEvent("startEvent")
                .serviceTask("serviceTask")
                .endEvent("endEvent")
                .done();

        wrap(model).taskAttributes("serviceTask", "foo", 6);

        process = new BpmnModelInstanceTransformer().transformSingleProcess(model, 0L);
        when(wfDefinitionCache.getProcessGraphByTypeId(process.id())).thenReturn(process);

        final FlowElementVisitor visitor = new FlowElementVisitor();
        serviceTaskId = visitor
                .init(process)
                .moveToNode(process.intialFlowNodeId())
                .traverseSingleOutgoingSequenceFlow()
                .nodeId();
    }

    @Test
    public void shouldDelegateToRegisteredBpmnAspectHandler()
    {
        // given
        final ActivityEventHandler handler = new ActivityEventHandler(wfDefinitionCache, idGenerator);

        final BpmnActivityInstanceAspectHandler aspectHandler = mock(BpmnActivityInstanceAspectHandler.class);
        when(aspectHandler.getHandledBpmnAspect()).thenReturn(BpmnAspect.TAKE_OUTGOING_FLOWS);

        handler.addAspectHandler(aspectHandler);

        final BpmnActivityEventReader eventReader = mock(BpmnActivityEventReader.class);
        when(eventReader.event()).thenReturn(ExecutionEventType.ACT_INST_COMPLETED);
        when(eventReader.flowElementId()).thenReturn(serviceTaskId);
        when(eventReader.wfDefinitionId()).thenReturn(process.id());
        when(eventReader.wfInstanceId()).thenReturn(123L);

        // when
        handler.handle(eventReader, responseControl, logWriters);

        // then
        verify(aspectHandler, times(1)).handle(eventReader, process, logWriters, idGenerator);
        verifyZeroInteractions(logWriters);
    }
}

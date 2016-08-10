package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.bpmn.graph.transformer.BpmnModelInstanceTransformer;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.wf.repository.WfDefinitionCache;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ProcessEventHandlerTest
{
    @Mock
    protected WfDefinitionCache wfDefinitionCache;

    @Mock
    protected LogWriter logWriter;

    @Mock
    protected IdGenerator idGenerator;

    @Mock
    protected ResponseControl responseControl;

    protected ProcessGraph process;
    protected int startEventId;

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

        startEventId = process.intialFlowNodeId();
    }

    @Test
    public void shouldDelegateToRegisteredBpmnAspectHandler()
    {
        // given
        final ProcessEventHandler handler = new ProcessEventHandler(wfDefinitionCache, logWriter, idGenerator);

        final BpmnProcessAspectHandler aspectHandler = mock(BpmnProcessAspectHandler.class);
        when(aspectHandler.getHandledBpmnAspect()).thenReturn(BpmnAspect.TAKE_OUTGOING_FLOWS);

        handler.addAspectHandler(aspectHandler);

        final BpmnProcessEventReader eventReader = mock(BpmnProcessEventReader.class);
        when(eventReader.event()).thenReturn(ExecutionEventType.PROC_INST_CREATED);
        when(eventReader.initialElementId()).thenReturn(startEventId);
        when(eventReader.processId()).thenReturn(process.id());
        when(eventReader.processInstanceId()).thenReturn(123L);

        // when
        handler.handle(eventReader, responseControl);

        // then
        verify(aspectHandler, times(1)).handle(eventReader, process, logWriter, idGenerator);
        verifyZeroInteractions(logWriter);
    }
}

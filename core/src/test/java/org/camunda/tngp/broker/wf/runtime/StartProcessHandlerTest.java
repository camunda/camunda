package org.camunda.tngp.broker.wf.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.wf.repository.handler.FluentAnswer;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class StartProcessHandlerTest
{

    @Mock
    protected BpmnFlowElementEventReader flowElementEventReader;

    @Mock
    protected ProcessGraph process;

    @Mock
    protected LogWriter logWriter;

    protected BpmnProcessEventWriter eventWriter;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        eventWriter = mock(BpmnProcessEventWriter.class, new FluentAnswer());
    }

    @Test
    public void shouldWriteStartProcessEvent()
    {
        // given
        final StartProcessHandler startProcessHandler = new StartProcessHandler();
        startProcessHandler.setEventWriter(eventWriter);

        when(flowElementEventReader.event()).thenReturn(ExecutionEventType.EVT_OCCURRED);
        when(flowElementEventReader.flowElementId()).thenReturn(42);
        when(flowElementEventReader.key()).thenReturn(53L);
        when(flowElementEventReader.processId()).thenReturn(1234L);
        when(flowElementEventReader.processInstanceId()).thenReturn(1701L);

        // when
        startProcessHandler.handle(flowElementEventReader, process, logWriter);

        // then
        verify(eventWriter).event(ExecutionEventType.PROC_INST_CREATED);
        verify(eventWriter).processId(1234L);
        verify(eventWriter).processInstanceId(1701L);
        verify(eventWriter).key(1701L);
        verify(eventWriter).initialElementId(42);
    }

    @Test
    public void shouldHandleStartProcessAspect()
    {
        // given
        final StartProcessHandler startProcessHandler = new StartProcessHandler();

        // when
        final BpmnAspect handledBpmnAspect = startProcessHandler.getHandledBpmnAspect();

        // then
        assertThat(handledBpmnAspect).isEqualTo(BpmnAspect.START_PROCESS);

    }
}

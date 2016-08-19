package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.broker.util.mocks.StubLogWriters;
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

public class StartProcessHandlerTest
{

    @Mock
    protected BpmnFlowElementEventReader flowElementEventReader;

    @Mock
    protected ProcessGraph process;

    protected StubLogWriter logWriter;
    protected StubLogWriters logWriters;

    protected IdGenerator idGenerator;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        idGenerator = new PrivateIdGenerator(0);

        logWriter = new StubLogWriter();
        logWriters = new StubLogWriters(0, logWriter);
    }

    @Test
    public void shouldWriteStartProcessEvent()
    {
        // given
        final StartProcessHandler startProcessHandler = new StartProcessHandler();

        when(flowElementEventReader.event()).thenReturn(ExecutionEventType.EVT_OCCURRED);
        when(flowElementEventReader.flowElementId()).thenReturn(42);
        when(flowElementEventReader.key()).thenReturn(53L);
        when(flowElementEventReader.wfDefinitionId()).thenReturn(1234L);
        when(flowElementEventReader.wfInstanceId()).thenReturn(1701L);

        // when
        startProcessHandler.handle(flowElementEventReader, process, logWriters, idGenerator);

        // then
        assertThat(logWriters.writtenEntries()).isEqualTo(1);
        final BpmnProcessEventReader reader = logWriter.getEntryAs(0, BpmnProcessEventReader.class);

        assertThat(reader.event()).isEqualTo(ExecutionEventType.PROC_INST_CREATED);
        assertThat(reader.processId()).isEqualTo(1234L);
        assertThat(reader.processInstanceId()).isEqualTo(1701L);
        assertThat(reader.key()).isEqualTo(1701L);
        assertThat(reader.initialElementId()).isEqualTo(42);

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

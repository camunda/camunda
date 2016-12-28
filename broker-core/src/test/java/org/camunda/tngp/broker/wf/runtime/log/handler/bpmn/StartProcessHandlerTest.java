package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;
import static org.mockito.Mockito.when;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.broker.util.mocks.StubLogWriters;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
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
        when(flowElementEventReader.payload()).thenReturn(new UnsafeBuffer(0, 0));

        // when
        startProcessHandler.handle(flowElementEventReader, process, logWriters, idGenerator);

        // then
        assertThat(logWriters.writtenEntries()).isEqualTo(2);
        final BpmnBranchEventReader branchEvent = logWriter.getEntryAs(0, BpmnBranchEventReader.class);
        final BpmnProcessEventReader processEvent = logWriter.getEntryAs(1, BpmnProcessEventReader.class);

        assertThatBuffer(branchEvent.materializedPayload()).hasCapacity(0);

        assertThat(processEvent.event()).isEqualTo(ExecutionEventType.PROC_INST_CREATED);
        assertThat(processEvent.processId()).isEqualTo(1234L);
        assertThat(processEvent.processInstanceId()).isEqualTo(1701L);
        assertThat(processEvent.key()).isEqualTo(1701L);
        assertThat(processEvent.initialElementId()).isEqualTo(42);

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

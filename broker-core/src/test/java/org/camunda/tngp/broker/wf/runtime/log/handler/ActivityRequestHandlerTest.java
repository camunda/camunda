package org.camunda.tngp.broker.wf.runtime.log.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.test.util.ArgumentAnswer;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.broker.util.mocks.StubLogWriters;
import org.camunda.tngp.broker.util.mocks.TestWfRuntimeLogEntries;
import org.camunda.tngp.broker.wf.runtime.log.ActivityInstanceRequestReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventWriter;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.protocol.log.ActivityInstanceRequestType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ActivityRequestHandlerTest
{

    protected StubLogReader logReader;
    protected StubLogWriter logWriter;

    protected StubLogWriters logWriters;

    @Mock
    protected Long2LongHashIndex index;

    @Mock
    protected ResponseControl responseControl;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        logReader = new StubLogReader(null);
        logWriter = new StubLogWriter();

        logWriters = new StubLogWriters(0, logWriter);
    }

    @Test
    public void shouldHandleCompleteRequest()
    {
        // given
        final ActivityRequestHandler handler = new ActivityRequestHandler(logReader, index);

        final BpmnActivityEventWriter activityInstanceEvent = TestWfRuntimeLogEntries.createActivityInstanceEvent(ExecutionEventType.ACT_INST_CREATED);
        logReader.addEntry(activityInstanceEvent);
        when(index.get(eq(TestWfRuntimeLogEntries.KEY), anyLong())).thenReturn(logReader.getEntryPosition(0));

        final ActivityInstanceRequestReader requestReader = mock(ActivityInstanceRequestReader.class);
        when(requestReader.activityInstanceKey()).thenReturn(TestWfRuntimeLogEntries.KEY);
        when(requestReader.type()).thenReturn(ActivityInstanceRequestType.COMPLETE);

        // when
        handler.handle(requestReader, responseControl, logWriters);

        // then
        assertThat(logWriters.writtenEntries()).isEqualTo(1);
        assertThat(logWriter.size()).isEqualTo(1);

        final BpmnActivityEventReader newLogEntry = logWriter.getEntryAs(0, BpmnActivityEventReader.class);
        assertThat(newLogEntry.key()).isEqualTo(TestWfRuntimeLogEntries.KEY);
        assertThat(newLogEntry.event()).isEqualTo(ExecutionEventType.ACT_INST_COMPLETED);
        assertThat(newLogEntry.flowElementId()).isEqualTo(TestWfRuntimeLogEntries.FLOW_ELEMENT_ID);
        assertThatBuffer(newLogEntry.getTaskType()).hasBytes(TestWfRuntimeLogEntries.TASK_TYPE);
        assertThat(newLogEntry.taskQueueId()).isEqualTo(TestWfRuntimeLogEntries.TASK_QUEUE_ID);
        assertThat(newLogEntry.wfDefinitionId()).isEqualTo(TestWfRuntimeLogEntries.PROCESS_ID);
        assertThat(newLogEntry.wfInstanceId()).isEqualTo(TestWfRuntimeLogEntries.PROCESS_INSTANCE_ID);
        assertThatBuffer(newLogEntry.getFlowElementIdString()).hasBytes(TestWfRuntimeLogEntries.FLOW_ELEMENT_ID_STRING);
    }

    @Test
    public void shouldIgnoreCompleteRequestForNonExistingActivityInstance()
    {
        // given
        final ActivityRequestHandler handler = new ActivityRequestHandler(logReader, index);

        when(index.get(anyLong(), anyLong())).thenAnswer(new ArgumentAnswer<>(1));

        final ActivityInstanceRequestReader requestReader = mock(ActivityInstanceRequestReader.class);
        when(requestReader.activityInstanceKey()).thenReturn(TestWfRuntimeLogEntries.KEY);
        when(requestReader.type()).thenReturn(ActivityInstanceRequestType.COMPLETE);

        // when
        handler.handle(requestReader, responseControl, logWriters);

        // then
        assertThat(logWriters.writtenEntries()).isEqualTo(0);
    }
}

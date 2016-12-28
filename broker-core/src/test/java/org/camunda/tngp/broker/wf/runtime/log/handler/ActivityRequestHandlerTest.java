package org.camunda.tngp.broker.wf.runtime.log.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.test.util.ArgumentAnswer;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.broker.util.mocks.StubLogWriters;
import org.camunda.tngp.broker.util.mocks.WfRuntimeEvents;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.protocol.log.ActivityInstanceRequestType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ActivityRequestHandlerTest
{

    public static final byte[] TASK_PAYLOAD = "taskPayload".getBytes(StandardCharsets.UTF_8);

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

        final String branchPayload = "payload";
        final BpmnBranchEventWriter bpmnBranchEvent = WfRuntimeEvents.bpmnBranchEvent(branchPayload, 999L);
        logReader.addEntry(bpmnBranchEvent);
        final BpmnActivityEventWriter activityInstanceEvent = WfRuntimeEvents.createActivityInstanceEvent(ExecutionEventType.ACT_INST_CREATED);
        activityInstanceEvent.bpmnBranchKey(999L);
        logReader.addEntry(activityInstanceEvent);

        when(index.get(eq(999L), anyLong())).thenReturn(logReader.getEntryPosition(0));
        when(index.get(eq(WfRuntimeEvents.KEY), anyLong())).thenReturn(logReader.getEntryPosition(1));

        final ActivityInstanceRequestReader requestReader = mock(ActivityInstanceRequestReader.class);
        when(requestReader.activityInstanceKey()).thenReturn(WfRuntimeEvents.KEY);
        when(requestReader.type()).thenReturn(ActivityInstanceRequestType.COMPLETE);
        when(requestReader.payload()).thenReturn(new UnsafeBuffer(TASK_PAYLOAD));


        // when
        handler.handle(requestReader, responseControl, logWriters);

        // then
        assertThat(logWriters.writtenEntries()).isEqualTo(2);
        assertThat(logWriter.size()).isEqualTo(2);

        final BpmnBranchEventReader newBpmnBranchEvent = logWriter.getEntryAs(0, BpmnBranchEventReader.class);

        final byte[] branchPayloadBytes = branchPayload.getBytes(StandardCharsets.UTF_8);
        final byte[] expectedPayload = Arrays.copyOf(branchPayloadBytes, branchPayloadBytes.length + TASK_PAYLOAD.length);
        System.arraycopy(TASK_PAYLOAD, 0, expectedPayload, branchPayloadBytes.length, TASK_PAYLOAD.length);

        assertThatBuffer(newBpmnBranchEvent.materializedPayload()).hasBytes(expectedPayload);
        assertThat(newBpmnBranchEvent.key()).isEqualTo(999L);

        final BpmnActivityEventReader newLogEntry = logWriter.getEntryAs(1, BpmnActivityEventReader.class);
        assertThat(newLogEntry.key()).isEqualTo(WfRuntimeEvents.KEY);
        assertThat(newLogEntry.event()).isEqualTo(ExecutionEventType.ACT_INST_COMPLETED);
        assertThat(newLogEntry.flowElementId()).isEqualTo(WfRuntimeEvents.FLOW_ELEMENT_ID);
        assertThatBuffer(newLogEntry.getTaskType()).hasBytes(WfRuntimeEvents.TASK_TYPE);
        assertThat(newLogEntry.taskQueueId()).isEqualTo(WfRuntimeEvents.TASK_QUEUE_ID);
        assertThat(newLogEntry.wfDefinitionId()).isEqualTo(WfRuntimeEvents.PROCESS_ID);
        assertThat(newLogEntry.wfInstanceId()).isEqualTo(WfRuntimeEvents.PROCESS_INSTANCE_ID);
        assertThatBuffer(newLogEntry.getFlowElementIdString()).hasBytes(WfRuntimeEvents.FLOW_ELEMENT_ID_STRING);
    }

    @Test
    public void shouldIgnoreCompleteRequestForNonExistingActivityInstance()
    {
        // given
        final ActivityRequestHandler handler = new ActivityRequestHandler(logReader, index);

        when(index.get(anyLong(), anyLong())).thenAnswer(new ArgumentAnswer<>(1));

        final ActivityInstanceRequestReader requestReader = mock(ActivityInstanceRequestReader.class);
        when(requestReader.activityInstanceKey()).thenReturn(WfRuntimeEvents.KEY);
        when(requestReader.type()).thenReturn(ActivityInstanceRequestType.COMPLETE);

        // when
        handler.handle(requestReader, responseControl, logWriters);

        // then
        assertThat(logWriters.writtenEntries()).isEqualTo(0);
    }
}

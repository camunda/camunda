package org.camunda.tngp.broker.wf.runtime.idx;

import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.camunda.tngp.broker.util.mocks.BpmnEventMocks;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnProcessEventReader;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.impl.Subscription;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WorkflowEventIndexWriterTest
{

    @Mock
    protected BpmnEventReader bpmnEventReader;

    @Mock
    protected BpmnActivityEventReader activityEventReader;

    @Mock
    protected BpmnFlowElementEventReader flowElementEventReader;

    @Mock
    protected BpmnProcessEventReader processEventReader;

    @Mock
    protected Long2LongHashIndex workflowEventIndex;

    @Mock
    protected Log log;

    @Mock
    protected Dispatcher dispatcher;

    @Mock
    protected Subscription subscription;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        when(log.getWriteBuffer()).thenReturn(dispatcher);
        when(dispatcher.openSubscription()).thenReturn(subscription);
    }

    @Test
    public void shouldWriteIndexOnActivityEvent()
    {
        // given
        final WorkflowEventIndexLogTracker indexWriter = new WorkflowEventIndexLogTracker(workflowEventIndex);
        BpmnEventMocks.mockActivityInstanceEvent(bpmnEventReader, activityEventReader, ExecutionEventType.ACT_INST_CREATED);

        // when
        indexWriter.onLogEntryCommit(bpmnEventReader, 76L);

        // then
        verify(workflowEventIndex).put(23456789L, 76L);
        verify(workflowEventIndex).resolveDirty(23456789L);
        verifyNoMoreInteractions(workflowEventIndex);

    }

    @Test
    public void shouldRemoveActivityInstanceOnComplete()
    {
        // given
        final WorkflowEventIndexLogTracker indexWriter = new WorkflowEventIndexLogTracker(workflowEventIndex);
        BpmnEventMocks.mockActivityInstanceEvent(bpmnEventReader, activityEventReader, ExecutionEventType.ACT_INST_COMPLETED);

        // when
        indexWriter.onLogEntryCommit(bpmnEventReader, 76L);

        // then
        verify(workflowEventIndex).remove(eq(23456789L), anyLong());
    }

    @Test
    public void shouldNotWriteIndexOnEventEvent()
    {
        // given
        final WorkflowEventIndexLogTracker indexWriter = new WorkflowEventIndexLogTracker(workflowEventIndex);
        BpmnEventMocks.mockFlowElementEvent(bpmnEventReader, flowElementEventReader);

        // when
        indexWriter.onLogEntryCommit(bpmnEventReader, 76L);

        // then
        verifyZeroInteractions(workflowEventIndex);

    }

    @Test
    public void shouldWriteIndexOnProcessEvent()
    {
        // given
        final WorkflowEventIndexLogTracker indexWriter = new WorkflowEventIndexLogTracker(workflowEventIndex);
        BpmnEventMocks.mockProcessEvent(bpmnEventReader, processEventReader);

        // when
        indexWriter.onLogEntryCommit(bpmnEventReader, 76L);

        // then
        verify(workflowEventIndex).put(BpmnEventMocks.KEY, 76L);
        verify(workflowEventIndex).resolveDirty(BpmnEventMocks.KEY);
        verifyNoMoreInteractions(workflowEventIndex);
    }

    @Test
    public void shouldRemoveProcessEventOnComplete()
    {
        // given
        final WorkflowEventIndexLogTracker indexWriter = new WorkflowEventIndexLogTracker(workflowEventIndex);
        BpmnEventMocks.mockProcessEvent(bpmnEventReader, processEventReader);
        when(processEventReader.event()).thenReturn(ExecutionEventType.PROC_INST_COMPLETED);

        // when
        indexWriter.onLogEntryCommit(bpmnEventReader, 76L);

        // then
        verify(workflowEventIndex).remove(BpmnEventMocks.KEY, -1L);
        verify(workflowEventIndex).resolveDirty(BpmnEventMocks.KEY);
        verifyNoMoreInteractions(workflowEventIndex);
    }
}

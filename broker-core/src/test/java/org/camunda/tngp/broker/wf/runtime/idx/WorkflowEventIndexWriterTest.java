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
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
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

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldWriteIndexOnActivityEvent()
    {
        // given
        final WorkflowEventIndexWriter indexWriter = new WorkflowEventIndexWriter(null, workflowEventIndex);
        BpmnEventMocks.mockActivityInstanceEvent(bpmnEventReader, activityEventReader, ExecutionEventType.ACT_INST_CREATED);

        // when
        indexWriter.handle(76L, bpmnEventReader);

        // then
        verify(workflowEventIndex).put(23456789L, 76L);
        verifyNoMoreInteractions(workflowEventIndex);

    }

    @Test
    public void shouldRemoveActivityInstanceOnComplete()
    {
        // given
        final WorkflowEventIndexWriter indexWriter = new WorkflowEventIndexWriter(null, workflowEventIndex);
        BpmnEventMocks.mockActivityInstanceEvent(bpmnEventReader, activityEventReader, ExecutionEventType.ACT_INST_COMPLETED);

        // when
        indexWriter.handle(76L, bpmnEventReader);

        // then
        verify(workflowEventIndex).remove(eq(23456789L), anyLong());
    }

    @Test
    public void shouldNotWriteIndexOnEventEvent()
    {
        // given
        final WorkflowEventIndexWriter indexWriter = new WorkflowEventIndexWriter(null, workflowEventIndex);
        BpmnEventMocks.mockFlowElementEvent(bpmnEventReader, flowElementEventReader);

        // when
        indexWriter.handle(76L, bpmnEventReader);

        // then
        verifyZeroInteractions(workflowEventIndex);

    }

    @Test
    public void shouldWriteIndexOnProcessEvent()
    {
        // given
        final WorkflowEventIndexWriter indexWriter = new WorkflowEventIndexWriter(null, workflowEventIndex);
        BpmnEventMocks.mockProcessEvent(bpmnEventReader, processEventReader);

        // when
        indexWriter.handle(76L, bpmnEventReader);

        // then
        verify(workflowEventIndex).put(BpmnEventMocks.KEY, 76L);
        verifyNoMoreInteractions(workflowEventIndex);
    }

    @Test
    public void shouldRemoveProcessEventOnComplete()
    {
        // given
        final WorkflowEventIndexWriter indexWriter = new WorkflowEventIndexWriter(null, workflowEventIndex);
        BpmnEventMocks.mockProcessEvent(bpmnEventReader, processEventReader);
        when(processEventReader.event()).thenReturn(ExecutionEventType.PROC_INST_COMPLETED);

        // when
        indexWriter.handle(76L, bpmnEventReader);

        // then
        verify(workflowEventIndex).remove(BpmnEventMocks.KEY, -1L);
        verifyNoMoreInteractions(workflowEventIndex);
    }
}

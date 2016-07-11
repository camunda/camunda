package org.camunda.tngp.broker.wf.runtime.idx;

import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

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

public class ActivityInstanceIndexWriterTest
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
    protected Long2LongHashIndex activityInstanceIndex;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldWriteIndexOnActivityEvent()
    {
        // given
        final ActivityInstanceIndexWriter indexWriter = new ActivityInstanceIndexWriter(null, activityInstanceIndex);
        BpmnEventMocks.mockActivityInstanceEvent(bpmnEventReader, activityEventReader, ExecutionEventType.ACT_INST_CREATED);

        // when
        indexWriter.handle(76L, bpmnEventReader);

        // then
        verify(activityInstanceIndex).put(23456789L, 76L);

    }

    @Test
    public void shouldRemoveActivityInstanceOnComplete()
    {
        // given
        final ActivityInstanceIndexWriter indexWriter = new ActivityInstanceIndexWriter(null, activityInstanceIndex);
        BpmnEventMocks.mockActivityInstanceEvent(bpmnEventReader, activityEventReader, ExecutionEventType.ACT_INST_COMPLETED);

        // when
        indexWriter.handle(76L, bpmnEventReader);

        // then
        verify(activityInstanceIndex).remove(eq(23456789L), anyLong());
    }

    @Test
    public void shouldNotWriteIndexOnEventEvent()
    {
        // given
        final ActivityInstanceIndexWriter indexWriter = new ActivityInstanceIndexWriter(null, activityInstanceIndex);
        BpmnEventMocks.mockFlowElementEvent(bpmnEventReader, flowElementEventReader);

        // when
        indexWriter.handle(76L, bpmnEventReader);

        // then
        verifyZeroInteractions(activityInstanceIndex);

    }

    @Test
    public void shouldNotWriteIndexOnProcessEvent()
    {
        // given
        final ActivityInstanceIndexWriter indexWriter = new ActivityInstanceIndexWriter(null, activityInstanceIndex);
        BpmnEventMocks.mockProcessEvent(bpmnEventReader, processEventReader);

        // when
        indexWriter.handle(76L, bpmnEventReader);

        // then
        verifyZeroInteractions(activityInstanceIndex);

    }
}

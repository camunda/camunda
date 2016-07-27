package org.camunda.tngp.broker.wf.runtime.idx;

import java.util.function.LongConsumer;

import org.camunda.tngp.broker.idx.LogEntryTracker;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;

public class WorkflowEventIndexLogTracker implements LogEntryTracker<BpmnEventReader>
{
    protected static final long IGNORE_EVENT = -1L;

    protected final Long2LongHashIndex workflowEventIndex;

    public WorkflowEventIndexLogTracker(Long2LongHashIndex workflowEventIndex)
    {
        this.workflowEventIndex = workflowEventIndex;
    }

    @Override
    public void onLogEntryStaged(BpmnEventReader logEntry)
    {
        onLogEntry(logEntry,
            (id) -> workflowEventIndex.markDirty(id),
            (id) -> workflowEventIndex.markDirty(id));
    }

    @Override
    public void onLogEntryFailed(BpmnEventReader logEntry)
    {
        onLogEntry(logEntry,
            (id) -> workflowEventIndex.resolveDirty(id),
            (id) -> workflowEventIndex.resolveDirty(id));
    }


    @Override
    public void onLogEntryCommit(BpmnEventReader logEntry, final long position)
    {
        onLogEntry(logEntry,
            (id) ->
            {
                workflowEventIndex.remove(id, -1L);
                workflowEventIndex.resolveDirty(id);
            },
            (id) ->
            {
                workflowEventIndex.put(id, position);
                workflowEventIndex.resolveDirty(id);
            });
    }

    public void onLogEntry(
            BpmnEventReader logEntry,
            LongConsumer onCompleteEvent,
            LongConsumer onAnyOtherEvent)
    {
        if (logEntry.isActivityEvent())
        {
            final BpmnActivityEventReader bpmnActivityEventReader = logEntry.activityEvent();
            final long activityInstanceId = bpmnActivityEventReader.key();

            if (bpmnActivityEventReader.event() == ExecutionEventType.ACT_INST_COMPLETED)
            {
                onCompleteEvent.accept(activityInstanceId);
            }
            else
            {
                onAnyOtherEvent.accept(activityInstanceId);
            }
        }
        else if (logEntry.isProcessEvent())
        {
            final BpmnProcessEventReader processEventReader = logEntry.processEvent();
            final long processInstanceId = processEventReader.key();

            if (processEventReader.event() == ExecutionEventType.PROC_INST_COMPLETED)
            {
                onCompleteEvent.accept(processInstanceId);
            }
            else
            {
                onAnyOtherEvent.accept(processInstanceId);
            }
        }
    }
}

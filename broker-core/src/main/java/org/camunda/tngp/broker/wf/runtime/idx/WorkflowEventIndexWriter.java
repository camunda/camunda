package org.camunda.tngp.broker.wf.runtime.idx;

import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;

// TODO: should write checkpoint on shutdown
public class WorkflowEventIndexWriter implements LogEntryHandler<BpmnEventReader>
{
    protected final Long2LongHashIndex workflowEventIndex;
    protected final LogEntryProcessor<BpmnEventReader> logEntryProcessor;

    public WorkflowEventIndexWriter(LogReader logReader, Long2LongHashIndex workflowEventIndex)
    {
        this.workflowEventIndex = workflowEventIndex;
        // TODO: reconstruct last read position
        this.logEntryProcessor = new LogEntryProcessor<>(logReader, new BpmnEventReader(), this);
    }

    public int update(int maxEntries)
    {
        return logEntryProcessor.doWork(maxEntries);
    }

    @Override
    public void handle(long position, BpmnEventReader reader)
    {
        if (reader.isActivityEvent())
        {
            final BpmnActivityEventReader bpmnActivityEventReader = reader.activityEvent();
            final long activityInstanceId = bpmnActivityEventReader.key();

            if (bpmnActivityEventReader.event() == ExecutionEventType.ACT_INST_COMPLETED)
            {
                workflowEventIndex.remove(activityInstanceId, -1);
            }
            else
            {
                workflowEventIndex.put(activityInstanceId, position);
            }
        }
        else if (reader.isProcessEvent())
        {
            final BpmnProcessEventReader processEventReader = reader.processEvent();
            final long processInstanceId = processEventReader.key();

            if (processEventReader.event() == ExecutionEventType.PROC_INST_COMPLETED)
            {
                workflowEventIndex.remove(processInstanceId, -1);
            }
            else
            {
                workflowEventIndex.put(processInstanceId, position);
            }
        }
    }
}

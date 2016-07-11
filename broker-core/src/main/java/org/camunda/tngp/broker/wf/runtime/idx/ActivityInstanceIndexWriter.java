package org.camunda.tngp.broker.wf.runtime.idx;

import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnEventReader;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.taskqueue.data.BpmnActivityEventDecoder;

// TODO: should write checkpoint on shutdown
public class ActivityInstanceIndexWriter implements LogEntryHandler<BpmnEventReader>
{
    protected final Long2LongHashIndex activityInstanceIndex;
    protected final LogEntryProcessor<BpmnEventReader> logEntryProcessor;

    public ActivityInstanceIndexWriter(LogReader logReader, Long2LongHashIndex activityInstanceIndex)
    {
        this.activityInstanceIndex = activityInstanceIndex;
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
        if (reader.templateId() == BpmnActivityEventDecoder.TEMPLATE_ID)
        {
            final BpmnActivityEventReader bpmnActivityEventReader = reader.activityEvent();
            final long activityInstanceId = bpmnActivityEventReader.key();

            if (bpmnActivityEventReader.event() == ExecutionEventType.ACT_INST_COMPLETED)
            {
                activityInstanceIndex.remove(activityInstanceId, -1);
            }
            else
            {
                activityInstanceIndex.put(activityInstanceId, position);
            }
        }
    }
}

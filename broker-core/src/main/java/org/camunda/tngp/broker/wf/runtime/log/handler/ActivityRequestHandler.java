package org.camunda.tngp.broker.wf.runtime.log.handler;

import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.wf.runtime.log.ActivityInstanceRequestReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventWriter;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.taskqueue.data.ActivityInstanceRequestType;

import uk.co.real_logic.agrona.DirectBuffer;

public class ActivityRequestHandler implements LogEntryTypeHandler<ActivityInstanceRequestReader>
{

    protected Long2LongHashIndex eventIndex;
    protected LogReader logReader;
    protected LogWriter logWriter;

    protected BpmnActivityEventReader activityInstanceReader = new BpmnActivityEventReader();
    protected BpmnActivityEventWriter activityInstanceWriter = new BpmnActivityEventWriter();

    public ActivityRequestHandler(LogReader logReader, LogWriter logWriter, Long2LongHashIndex eventIndex)
    {
        this.eventIndex = eventIndex;
        this.logReader = logReader;
        this.logWriter = logWriter;
    }

    @Override
    public void handle(ActivityInstanceRequestReader requestReader, ResponseControl responseControl)
    {

        if (requestReader.type() == ActivityInstanceRequestType.COMPLETE)
        {
            completeActivityInstance(requestReader);
        }
    }

    protected void completeActivityInstance(ActivityInstanceRequestReader requestReader)
    {
        final long activityInstanceId = requestReader.activityInstanceKey();
        final long latestPosition = eventIndex.get(activityInstanceId, -1);

        if (latestPosition < 0)
        {
            // activity instance does not exist; ignore
            return;
        }

        logReader.setPosition(latestPosition);
        logReader.read(activityInstanceReader);

        activityInstanceWriter
            .eventType(ExecutionEventType.ACT_INST_COMPLETED)
            .flowElementId(activityInstanceReader.flowElementId())
            .key(activityInstanceReader.key())
            .wfDefinitionId(activityInstanceReader.wfDefinitionId())
            .wfInstanceId(activityInstanceReader.wfInstanceId())
            .taskQueueId(activityInstanceReader.taskQueueId());

        final DirectBuffer taskType = activityInstanceReader.getTaskType();

        activityInstanceWriter
            .taskType(taskType, 0, taskType.capacity());

        logWriter.write(activityInstanceWriter);

    }


}

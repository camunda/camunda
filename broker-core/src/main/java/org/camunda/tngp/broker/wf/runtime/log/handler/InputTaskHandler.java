package org.camunda.tngp.broker.wf.runtime.log.handler;

import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.taskqueue.TaskInstanceReader;
import org.camunda.tngp.broker.wf.runtime.log.ActivityInstanceRequestWriter;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;

public class InputTaskHandler implements LogEntryTypeHandler<TaskInstanceReader>
{

    protected ActivityInstanceRequestWriter logRequestWriter = new ActivityInstanceRequestWriter();

    @Override
    public void handle(TaskInstanceReader reader, ResponseControl responseControl, LogWriters logWriters)
    {
        if (reader.state() == TaskInstanceState.COMPLETED && reader.wfActivityInstanceEventKey() != TaskInstanceDecoder.wfActivityInstanceEventKeyNullValue())
        {
            final int wfRuntimeLogId = reader.wfRuntimeResourceId();

            logRequestWriter
                .source(EventSource.LOG)
                .key(reader.wfActivityInstanceEventKey());

            logWriters.writeToLog(wfRuntimeLogId, logRequestWriter);
        }

    }

}

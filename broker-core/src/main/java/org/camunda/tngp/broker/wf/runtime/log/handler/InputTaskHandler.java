package org.camunda.tngp.broker.wf.runtime.log.handler;

import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.log.ResponseControl;
import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.wf.runtime.log.ActivityInstanceRequestWriter;
import org.camunda.tngp.protocol.log.TaskInstanceDecoder;
import org.camunda.tngp.protocol.log.TaskInstanceState;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;

public class InputTaskHandler implements LogEntryTypeHandler<TaskInstanceReader>
{

    protected ActivityInstanceRequestWriter logRequestWriter = new ActivityInstanceRequestWriter();

    @Override
    public void handle(TaskInstanceReader reader, ResponseControl responseControl, LogWriters logWriters)
    {
        if (reader.state() == TaskInstanceState.COMPLETED && reader.wfActivityInstanceEventKey() != TaskInstanceDecoder.wfActivityInstanceEventKeyNullValue())
        {
            final int wfRuntimeLogId = reader.wfRuntimeResourceId();
            final DirectBuffer payload = reader.getPayload();

            logRequestWriter
                .source(EventSource.LOG)
                .key(reader.wfActivityInstanceEventKey())
                .payload(payload, 0, payload.capacity());

            logWriters.writeToLog(wfRuntimeLogId, logRequestWriter);
        }

    }

}

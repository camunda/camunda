package org.camunda.tngp.broker.taskqueue.log.handler;

import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.taskqueue.LockedTaskBatchWriter;
import org.camunda.tngp.broker.taskqueue.SingleTaskAckResponseWriter;
import org.camunda.tngp.protocol.log.TaskInstanceState;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;
import org.agrona.DirectBuffer;

public class TaskInstanceHandler implements LogEntryTypeHandler<TaskInstanceReader>
{
    protected SingleTaskAckResponseWriter singleTaskResponseWriter = new SingleTaskAckResponseWriter();
    protected LockedTaskBatchWriter taskBatchResponseWriter = new LockedTaskBatchWriter();

    @Override
    public void handle(TaskInstanceReader reader, ResponseControl responseControl, LogWriters logWriters)
    {
        final TaskInstanceState state = reader.state();

        if (state == TaskInstanceState.NEW)
        {
            singleTaskResponseWriter.taskId(reader.id());

            responseControl.accept(singleTaskResponseWriter);
        }
        else if (state == TaskInstanceState.LOCKED)
        {
            final DirectBuffer payload = reader.getPayload();

            taskBatchResponseWriter
                .consumerId((int) reader.lockOwnerId()) // TODO: clarifiy: int or long?
                .lockTime(reader.lockTime())
                .newTasks()
                    .appendTask(reader.id(), reader.wfInstanceId(), payload, 0, payload.capacity());

            responseControl.accept(taskBatchResponseWriter);
        }
    }

}

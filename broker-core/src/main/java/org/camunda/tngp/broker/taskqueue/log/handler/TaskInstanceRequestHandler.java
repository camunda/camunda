package org.camunda.tngp.broker.taskqueue.log.handler;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.taskqueue.SingleTaskAckResponseWriter;
import org.camunda.tngp.broker.taskqueue.TaskErrors;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceRequestReader;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.log.TaskInstanceEncoder;
import org.camunda.tngp.protocol.log.TaskInstanceRequestType;
import org.camunda.tngp.protocol.log.TaskInstanceState;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;

public class TaskInstanceRequestHandler implements LogEntryTypeHandler<TaskInstanceRequestReader>
{

    protected LogReader logReader;
    protected Long2LongHashIndex lockedTasksIndex;

    protected TaskInstanceReader taskInstanceReader = new TaskInstanceReader();
    protected TaskInstanceWriter taskInstanceWriter = new TaskInstanceWriter();

    protected SingleTaskAckResponseWriter responseWriter = new SingleTaskAckResponseWriter();
    protected ErrorWriter errorWriter = new ErrorWriter();

    public TaskInstanceRequestHandler(LogReader logReader, Long2LongHashIndex lockedTasksIndex)
    {
        this.logReader = logReader;
        this.lockedTasksIndex = lockedTasksIndex;
    }

    @Override
    public void handle(TaskInstanceRequestReader reader, ResponseControl responseControl, LogWriters logWriters)
    {
        if (reader.type() == TaskInstanceRequestType.COMPLETE)
        {
            final long lockedTaskPosition = lockedTasksIndex.get(reader.key(), -1L);

            if (lockedTaskPosition < 0)
            {
                reject(responseControl, TaskErrors.COMPLETE_TASK_ERROR, "Task does not exist or is not locked");
            }
            else
            {
                logReader.seek(lockedTaskPosition);
                logReader.next()
                    .readValue(taskInstanceReader);

                final long requestConsumer = reader.consumerId();

                if (taskInstanceReader.lockOwnerId() != requestConsumer)
                {
                    reject(responseControl, TaskErrors.COMPLETE_TASK_ERROR, "Task is currently not locked by the provided consumer");
                }
                else
                {
                    final DirectBuffer payload = reader.payload();
                    final DirectBuffer taskType = taskInstanceReader.getTaskType();

                    taskInstanceWriter
                        .source(EventSource.LOG)
                        .id(taskInstanceReader.id())
                        .lockOwner(TaskInstanceEncoder.lockOwnerIdNullValue())
                        .lockTime(TaskInstanceEncoder.lockTimeNullValue())
                        .payload(payload, 0, payload.capacity())
                        .taskType(taskType, 0, taskType.capacity())
                        .prevVersionPosition(lockedTaskPosition)
                        .state(TaskInstanceState.COMPLETED)
                        .wfActivityInstanceEventKey(taskInstanceReader.wfActivityInstanceEventKey())
                        .wfRuntimeResourceId(taskInstanceReader.wfRuntimeResourceId())
                        .wfInstanceId(taskInstanceReader.wfInstanceId());

                    logWriters.writeToCurrentLog(taskInstanceWriter);

                    responseWriter.taskId(taskInstanceReader.id());

                    responseControl.accept(responseWriter);
                }
            }
        }
    }

    protected void reject(ResponseControl responseControl, int errorCode, String errorMessage)
    {
        errorWriter
            .componentCode(TaskErrors.COMPONENT_CODE)
            .detailCode(errorCode)
            .errorMessage(errorMessage);

        responseControl.reject(errorWriter);
    }

}

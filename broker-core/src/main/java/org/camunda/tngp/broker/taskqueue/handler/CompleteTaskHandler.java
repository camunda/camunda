package org.camunda.tngp.broker.taskqueue.handler;

import org.camunda.tngp.broker.taskqueue.CompleteTaskRequestReader;
import org.camunda.tngp.broker.taskqueue.SingleTaskAckResponseWriter;
import org.camunda.tngp.broker.taskqueue.TaskErrors;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogReaderImpl;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.taskqueue.CompleteTaskEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestHandler;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.ResponseCompletionHandler;

import uk.co.real_logic.agrona.DirectBuffer;

public class CompleteTaskHandler implements BrokerRequestHandler<TaskQueueContext>, ResponseCompletionHandler
{
    protected CompleteTaskRequestReader requestReader = new CompleteTaskRequestReader();
    protected SingleTaskAckResponseWriter responseWriter = new SingleTaskAckResponseWriter();

    protected TaskInstanceReader taskInstanceReader = new TaskInstanceReader();
    protected TaskInstanceWriter taskInstanceWriter = new TaskInstanceWriter();
    protected ErrorWriter errorWriter = new ErrorWriter();

    protected static final int READ_BUFFER_SIZE = 1024 * 1024;
    protected LogReader logReader = new LogReaderImpl(READ_BUFFER_SIZE);

    @Override
    public long onRequest(
            final TaskQueueContext ctx,
            final DirectBuffer msg,
            final int offset,
            final int length,
            final DeferredResponse response)
    {
        final Long2LongHashIndex lockedTasksIndex = ctx.getLockedTaskInstanceIndex().getIndex();
        final Log log = ctx.getLog();

        requestReader.wrap(msg, offset, length);

        final int consumerId = requestReader.consumerId();
        if (consumerId == CompleteTaskEncoder.consumerIdNullValue())
        {
            return writeError(response, "Consumer id is required");
        }

        final long taskId = requestReader.taskId();
        if (taskId == CompleteTaskEncoder.taskIdNullValue())
        {
            return writeError(response, "Task id is required");
        }

        final long lastTaskPosition = lockedTasksIndex.get(taskId, -1, AsyncRequestHandler.POSTPONE_RESPONSE_CODE);

        if (lastTaskPosition >= 0)
        {
            logReader.setLogAndPosition(log, lastTaskPosition);
            logReader.read(taskInstanceReader);

            if (taskInstanceReader.lockOwnerId() != consumerId)
            {
                return writeError(response, "Task is currently not locked by the provided consumer");
            }

            responseWriter.taskId(taskId);

            if (response.allocateAndWrite(responseWriter))
            {

                final DirectBuffer payload = taskInstanceReader.getPayload();
                final DirectBuffer taskType = taskInstanceReader.getTaskType();

                taskInstanceWriter
                    .id(taskInstanceReader.id())
                    .lockOwner(TaskInstanceEncoder.lockOwnerIdNullValue())
                    .lockTime(TaskInstanceEncoder.lockTimeNullValue())
                    .payload(payload, 0, payload.capacity())
                    .taskType(taskType, 0, taskType.capacity())
                    .prevVersionPosition(lastTaskPosition)
                    .state(TaskInstanceState.COMPLETED)
                    .wfActivityInstanceEventKey(taskInstanceReader.wfActivityInstanceEventKey())
                    .wfRuntimeResourceId(taskInstanceReader.wfRuntimeResourceId());

                final LogWriter logWriter = ctx.getLogWriter();
                final long logEntryOffset = logWriter.write(taskInstanceWriter);

                return response.defer(logEntryOffset, this);
            }
            else
            {
                // TODO: backpressure
                return -1;
            }
        }
        else if (lastTaskPosition == AsyncRequestHandler.POSTPONE_RESPONSE_CODE)
        {
            return AsyncRequestHandler.POSTPONE_RESPONSE_CODE;
        }
        else
        {
            return writeError(response, "Task does not exist or is not locked");
        }

    }

    protected int writeError(DeferredResponse response, String errorMessage)
    {
        errorWriter
            .componentCode(TaskErrors.COMPONENT_CODE)
            .detailCode(TaskErrors.COMPLETE_TASK_ERROR)
            .errorMessage(errorMessage);

        if (response.allocateAndWrite(errorWriter))
        {
            response.commit();
            return 0;
        }
        else
        {
            return -1;
        }
    }

    @Override
    public void onAsyncWorkCompleted(final DeferredResponse response)
    {
        response.commit();
    }

    @Override
    public void onAsyncWorkFailed(final DeferredResponse response)
    {
        response.abort();
    }

}

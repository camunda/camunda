package org.camunda.tngp.broker.taskqueue.request.handler;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.taskqueue.LockedTaskBatchWriter;
import org.camunda.tngp.broker.taskqueue.PollAndLockTaskRequestReader;
import org.camunda.tngp.broker.taskqueue.TaskErrors;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.taskqueue.subscription.LockTasksOperator;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.taskqueue.PollAndLockTasksDecoder;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;
import org.camunda.tngp.protocol.wf.Constants;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.ResponseCompletionHandler;

public class LockTaskBatchHandler implements BrokerRequestHandler<TaskQueueContext>, ResponseCompletionHandler
{
    protected PollAndLockTaskRequestReader requestReader = new PollAndLockTaskRequestReader();
    protected LockedTaskBatchWriter responseWriter = new LockedTaskBatchWriter();
    protected TaskInstanceReader taskInstanceReader = new TaskInstanceReader();
    protected TaskInstanceWriter taskInstanceWriter = new TaskInstanceWriter();
    protected ErrorWriter errorWriter = new ErrorWriter();

    @Override
    public long onRequest(
            final TaskQueueContext ctx,
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final DeferredResponse response)
    {
        final LockTasksOperator lockedTasksOperator = ctx.getLockedTasksOperator();

        requestReader.wrap(buffer, offset, length);

        final int consumerId = requestReader.consumerId();
        if (consumerId == PollAndLockTasksDecoder.consumerIdNullValue())
        {
            return writeError(response, "Consumer id is required");
        }

        final long lockTime = requestReader.lockTime();
        if (lockTime == PollAndLockTasksDecoder.lockTimeNullValue())
        {
            return writeError(response, "Lock time is required");
        }

        final DirectBuffer taskType = requestReader.taskType();
        if (taskType.capacity() == 0)
        {
            return writeError(response, "Task type is required");
        }
        if (taskType.capacity() > Constants.TASK_TYPE_MAX_LENGTH)
        {
            return writeError(response, "Task type is too long");
        }

        response.defer();
        lockedTasksOperator.openAdhocSubscription(
                response,
                requestReader.consumerId(),
                requestReader.lockTime(),
                requestReader.maxTasks(),
                requestReader.taskType());

        return 0;
    }

    protected int writeError(DeferredResponse response, String errorMessage)
    {
        errorWriter
            .componentCode(TaskErrors.COMPONENT_CODE)
            .detailCode(TaskErrors.LOCK_TASKS_ERROR)
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

    @Override
    public int getTemplateId()
    {
        return PollAndLockTasksDecoder.TEMPLATE_ID;
    }
}

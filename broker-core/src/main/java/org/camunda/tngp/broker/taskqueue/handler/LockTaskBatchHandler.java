package org.camunda.tngp.broker.taskqueue.handler;

import org.camunda.tngp.broker.taskqueue.LockedTaskBatchWriter;
import org.camunda.tngp.broker.taskqueue.PollAndLockTaskRequestReader;
import org.camunda.tngp.broker.taskqueue.TaskErrors;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchEncoder;
import org.camunda.tngp.protocol.wf.Constants;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.ResponseCompletionHandler;

import uk.co.real_logic.agrona.DirectBuffer;

public class LockTaskBatchHandler implements BrokerRequestHandler<TaskQueueContext>, ResponseCompletionHandler
{
    protected PollAndLockTaskRequestReader requestReader = new PollAndLockTaskRequestReader();
    protected LockedTaskBatchWriter responseWriter = new LockedTaskBatchWriter();
    protected TaskInstanceReader taskInstanceReader = new TaskInstanceReader();
    protected TaskInstanceWriter taskInstanceWriter = new TaskInstanceWriter();
    protected ErrorWriter errorWriter = new ErrorWriter();

    protected LockableTaskFinder lockableTaskFinder = new LockableTaskFinder();

    @Override
    public long onRequest(
            final TaskQueueContext ctx,
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final DeferredResponse response)
    {
        final Bytes2LongHashIndex taskTypePositionIndex = ctx.getTaskTypePositionIndex().getIndex();
        final Log log = ctx.getLog();

        requestReader.wrap(buffer, offset, length);

        final int consumerId = requestReader.consumerId();
        if (consumerId == LockedTaskBatchEncoder.consumerIdNullValue())
        {
            return writeError(response, "Consumer id is required");
        }


        final long lockTime = requestReader.lockTime();
        if (lockTime == LockedTaskBatchEncoder.lockTimeNullValue())
        {
            return writeError(response, "Lock time is required");
        }

//        final long maxTask = decoder.maxTasks();

        final DirectBuffer taskType = requestReader.taskType();
        if (taskType.capacity() == 0)
        {
            return writeError(response, "Task type is required");
        }
        if (taskType.capacity() > Constants.TASK_TYPE_MAX_LENGTH)
        {
            return writeError(response, "Task type is too long");
        }

        final int taskTypeHash = TaskTypeHash.hashCode(taskType, 0, taskType.capacity());

        final long now = System.currentTimeMillis();
        final long lockTimeout = now + lockTime;

        // scan the log for lockable tasks
        final long scanPos = Math.max(taskTypePositionIndex.get(taskType, 0, taskType.capacity(), -1), log.getInitialPosition());
        lockableTaskFinder.init(log, scanPos, taskTypeHash, taskType);

        final boolean lockableTaskFound = lockableTaskFinder.findNextLockableTask();

        if (lockableTaskFound)
        {
            return lockTask(
                    response,
                    consumerId,
                    lockTimeout,
                    lockableTaskFinder.getLockableTask(),
                    lockableTaskFinder.getLockableTaskPosition(),
                    ctx.getLogWriter());
        }
        else
        {
            return lockNoTask(response, consumerId, lockTimeout);
        }
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

    protected int lockTask(
            final DeferredResponse response,
            final int consumerId,
            final long lockTimeout,
            TaskInstanceReader lockableTask,
            long lockableTaskPosition,
            LogWriter logWriter)
    {
        final DirectBuffer taskPayload = lockableTask.getPayload();

        initResponse(consumerId, lockTimeout);
        responseWriter
            .appendTask(lockableTask.id(), taskPayload, 0, taskPayload.capacity());

        if (response.allocateAndWrite(responseWriter))
        {
            final DirectBuffer taskType = lockableTask.getTaskType();
            final DirectBuffer payload = lockableTask.getPayload();

            taskInstanceWriter
                .id(lockableTask.id())
                .wfActivityInstanceEventKey(lockableTask.wfActivityInstanceEventKey())
                .wfRuntimeResourceId(lockableTask.wfRuntimeResourceId())
                .lockOwner(consumerId)
                .lockTime(lockTimeout)
                .state(TaskInstanceState.LOCKED)
                .prevVersionPosition(lockableTaskPosition)
                .payload(payload, 0, payload.capacity())
                .taskType(taskType, 0, taskType.capacity());

            final long logEntryOffset = logWriter.write(taskInstanceWriter);
            return response.defer(logEntryOffset, this);
        }
        else
        {
            return -1;
        }
    }

    protected void initResponse(
            final int consumerId,
            final long lockTimeout)
    {
        responseWriter
            .consumerId(consumerId)
            .lockTime(lockTimeout)
            .newTasks();
    }

    protected int lockNoTask(
            final DeferredResponse response,
            final int consumerId,
            final long lockTimeout)
    {
        initResponse(consumerId, lockTimeout);

        if (response.allocateAndWrite(responseWriter))
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

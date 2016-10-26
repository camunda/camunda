package org.camunda.tngp.broker.taskqueue.subscription;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap.KeyIterator;
import org.agrona.collections.Long2ObjectHashMap.KeySet;
import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.log.LogWriter;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.request.handler.LockableTaskFinder;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.camunda.tngp.protocol.log.TaskInstanceState;
import org.camunda.tngp.protocol.taskqueue.LockedTaskWriter;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.singlemessage.DataFramePool;

public class LockTasksOperator
{

    public static final long MISSING_VALUE = -1L;

    protected IdGenerator subscriptionIdGenerator = new PrivateIdGenerator(0L);

    // should be safe to access index, since index/log processing is done in same thread context (i.e. not in parallel)
    // it should also be ok to see any snapshot when making the lock operation; something that is not yet visible
    // becomes visible on one of the next lock cycles
    protected Bytes2LongHashIndex taskTypePositionIndex;

    // not required anymore with symbolic positions
    protected static final int INITIAL_LOG_POSITION = 264;

    protected LogWriter logWriter;
    protected LockableTaskFinder taskFinder;

    protected TaskInstanceWriter taskInstanceWriter = new TaskInstanceWriter();

    protected Long2ObjectHashMap<TaskSubscription> taskSubscriptions = new Long2ObjectHashMap<>();
    protected Long2LongHashMap pendingLockedTasks = new Long2LongHashMap(MISSING_VALUE);

    protected DataFramePool dataFramePool;

    protected LockedTaskWriter taskWriter = new LockedTaskWriter();

    public LockTasksOperator(
            Bytes2LongHashIndex taskTypePositionIndex,
            LogReader logReader,
            LogWriter logWriter,
            DataFramePool dataFramePool)
    {
        this.taskTypePositionIndex = taskTypePositionIndex;
        this.logWriter = logWriter;
        this.dataFramePool = dataFramePool;
        this.taskFinder = new LockableTaskFinder(logReader);
    }

    public int lockTasks()
    {
        final KeySet keySet = taskSubscriptions.keySet();
        final KeyIterator keyIterator = keySet.iterator();

        int tasksLocked = 0;

        while (keyIterator.hasNext())
        {
            final TaskSubscription subscription = taskSubscriptions.get(keyIterator.nextLong());

            if (subscription.getCredits() > 0)
            {
                tasksLocked += lockTasks(subscription);
            }
        }

        return tasksLocked;
    }

    public int lockTasks(TaskSubscription subscription)
    {

        // scan the log for lockable tasks
        final DirectBuffer taskType = subscription.getTaskType();
        final long recentTaskTypePosition = taskTypePositionIndex.get(taskType, 0, taskType.capacity(), -1);

        final long scanPos = Math.max(recentTaskTypePosition, 0);
        taskFinder.init(scanPos, subscription.getTaskTypeHash(), taskType);

        final boolean lockableTaskFound = taskFinder.findNextLockableTask();
        final int numTasksFound = lockableTaskFound ? 1 : 0;

        if (lockableTaskFound)
        {
            lockTask(
                    subscription,
                    taskFinder.getLockableTask(),
                    taskFinder.getLockableTaskPosition(),
                    logWriter);

            subscription.setCredits(subscription.getCredits() - 1);
        }

        subscription.onTaskAcquisition(this, numTasksFound);

        return numTasksFound;
    }

    protected void lockTask(
            TaskSubscription subscription,
            TaskInstanceReader lockableTask,
            long lockableTaskPosition,
            LogWriter logWriter)
    {

        final long now = System.currentTimeMillis();
        final long lockTimeout = now + subscription.getLockDuration();

        final DirectBuffer taskType = lockableTask.getTaskType();
        final DirectBuffer payload = lockableTask.getPayload();

        taskInstanceWriter
            .source(EventSource.NULL_VAL) // TODO: dedicated value?
            .id(lockableTask.id())
            .wfActivityInstanceEventKey(lockableTask.wfActivityInstanceEventKey())
            .wfRuntimeResourceId(lockableTask.wfRuntimeResourceId())
            .wfInstanceId(lockableTask.wfInstanceId())
            .lockOwner(subscription.getConsumerId())
            .lockTime(lockTimeout)
            .state(TaskInstanceState.LOCKED)
            .prevVersionPosition(lockableTaskPosition)
            .payload(payload, 0, payload.capacity())
            .taskType(taskType, 0, taskType.capacity());

        logWriter.write(taskInstanceWriter);

        pendingLockedTasks.put(lockableTask.id(), subscription.getId());

    }

    public void onTaskLocked(TaskInstanceReader task)
    {
        final long taskId = task.id();

        final long subscriptionId = pendingLockedTasks.remove(taskId);

        if (subscriptionId == MISSING_VALUE)
        {
            System.err.println("Ignoring locked task " + taskId + " that is not related to a subscription");
            return;
        }

        final TaskSubscription subscription = taskSubscriptions.get(subscriptionId);

        if (subscription == null)
        {
            System.err.println("Ignoring locked task " + taskId + ". Subscription " + subscriptionId + " is not open");
            return;
        }

        taskWriter.id(taskId)
            .lockTime(task.lockTime())
            .workflowInstanceId(task.wfInstanceId());

        subscription.onTaskLocked(this, taskWriter);
    }

    public boolean hasPendingTasks()
    {
        return !pendingLockedTasks.isEmpty();
    }

    protected void addTaskSubscription(TaskSubscription subscription)
    {
        taskSubscriptions.put(subscription.getId(), subscription);
    }

    public TaskSubscription openSubscription(
            int channelId,
            int consumerId,
            long lockDuration,
            long credits,
            DirectBuffer taskType)
    {
        final TaskSubscription subscription = new OngoingTaskSubscription(
                subscriptionIdGenerator.nextId(),
                channelId,
                dataFramePool);
        addTaskSubscription(subscription);

        subscription.setConsumerId(consumerId);
        subscription.setCredits(credits);
        subscription.setLockDuration(lockDuration);
        subscription.setTaskType(taskType, 0, taskType.capacity());

        return subscription;
    }

    public TaskSubscription openAdhocSubscription(
            DeferredResponse response,
            int consumerId,
            long lockDuration,
            long credits,
            DirectBuffer taskType)
    {
        final AdhocTaskSubscription subscription = new AdhocTaskSubscription(subscriptionIdGenerator.nextId());
        subscription.wrap(response);
        addTaskSubscription(subscription);

        subscription.setConsumerId(consumerId);
        subscription.setCredits(credits);
        subscription.setLockDuration(lockDuration);
        subscription.setTaskType(taskType, 0, taskType.capacity());

        return subscription;
    }

    public void removeSubscription(TaskSubscription subscription)
    {
        taskSubscriptions.remove(subscription.getId());
    }

    public TaskSubscription getSubscription(long subscriptionId)
    {
        return taskSubscriptions.get(subscriptionId);
    }
}

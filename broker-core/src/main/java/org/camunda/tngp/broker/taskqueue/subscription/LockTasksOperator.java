package org.camunda.tngp.broker.taskqueue.subscription;

import java.util.Queue;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
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
import org.camunda.tngp.util.BoundedArrayQueue;

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

    protected Queue<AdhocTaskSubscription> adhocSubscriptionPool;

    protected Int2ObjectHashMap<TaskSubscription> subscriptionsByType = new Int2ObjectHashMap<>();

    public LockTasksOperator(
            Bytes2LongHashIndex taskTypePositionIndex,
            LogReader logReader,
            LogWriter logWriter,
            DataFramePool dataFramePool,
            int upperBoundConcurrentAdhocSubscriptions)
    {
        this.taskTypePositionIndex = taskTypePositionIndex;
        this.logWriter = logWriter;
        this.dataFramePool = dataFramePool;
        this.taskFinder = new LockableTaskFinder(logReader);
        this.adhocSubscriptionPool = new BoundedArrayQueue<>(upperBoundConcurrentAdhocSubscriptions);

        for (int i = 0; i < upperBoundConcurrentAdhocSubscriptions; i++)
        {
            adhocSubscriptionPool.add(new AdhocTaskSubscription(subscriptionIdGenerator.nextId()));
        }
    }

    public int lockTasks()
    {

        // determine scan start position
        final KeySet keySet = taskSubscriptions.keySet();
        long scanStartPosition = Long.MAX_VALUE;
        KeyIterator subscriptionIdIt = keySet.iterator();

        while (subscriptionIdIt.hasNext())
        {
            final TaskSubscription subscription = taskSubscriptions.get(subscriptionIdIt.nextLong());
            final DirectBuffer taskType = subscription.getTaskType();
            final long taskTypePosition = taskTypePositionIndex.get(taskType, 0, taskType.capacity(), -1);

            if (taskTypePosition >= 0 && taskTypePosition < scanStartPosition)
            {
                scanStartPosition = taskTypePosition;
            }
        }

        if (scanStartPosition == Long.MAX_VALUE)
        {
            scanStartPosition = 0;
        }

        // scan for tasks
        taskFinder.init(scanStartPosition, subscriptionsByType.keySet());

        int numTasksFound = 0;

        while (taskFinder.findNextLockableTask())
        {
            final TaskInstanceReader task = taskFinder.getLockableTask();
            final DirectBuffer foundTaskType = task.getTaskType();
            final long taskTypeBeginIndex = taskTypePositionIndex.get(foundTaskType, 0, foundTaskType.capacity(), -1L);

            if (taskFinder.getLockableTaskPosition() < taskTypeBeginIndex)
            {
                // ignore task for which the index says that it has already been locked
                continue;
            }

            final TaskSubscription headSubscription = subscriptionsByType.get((int) task.taskTypeHash());
            TaskSubscription candidateSubscription = headSubscription;
            boolean subscriptionFound = false;

            do
            {
                final DirectBuffer taskType = candidateSubscription.getTaskType();

                if (candidateSubscription.getCredits() > 0L &&
                        LockableTaskFinder.taskTypeEqual(taskType, foundTaskType))
                {
                    subscriptionFound = true;
                }
                else
                {
                    candidateSubscription = candidateSubscription.getNext();
                }
            }
            while (!subscriptionFound && candidateSubscription != headSubscription);

            if (subscriptionFound)
            {
                lockTask(
                        candidateSubscription,
                        taskFinder.getLockableTask(),
                        taskFinder.getLockableTaskPosition(),
                        logWriter);
                numTasksFound++;
                candidateSubscription.setCredits(candidateSubscription.getCredits() - 1);
            }

            // TODO: could rotate in tasktypemap to ensure fairness
        }

        subscriptionIdIt = keySet.iterator();
        while (subscriptionIdIt.hasNext())
        {
            taskSubscriptions.get(subscriptionIdIt.nextLong()).onTaskAcquisitionFinished(this);
        }

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

        final int taskTypeHash = subscription.getTaskTypeHash();
        if (subscriptionsByType.containsKey(taskTypeHash))
        {
            final TaskSubscription next = subscriptionsByType.get(taskTypeHash);
            subscription.setNext(next);
            final TaskSubscription previous = next.getPrevious();
            subscription.setPrevious(previous);
            previous.setNext(subscription);
            next.setPrevious(subscription);
        }

        subscriptionsByType.put(taskTypeHash, subscription);
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

        subscription.setConsumerId(consumerId);
        subscription.setCredits(credits);
        subscription.setLockDuration(lockDuration);
        subscription.setTaskType(taskType, 0, taskType.capacity());

        addTaskSubscription(subscription);

        return subscription;
    }

    public TaskSubscription openAdhocSubscription(
            DeferredResponse response,
            int consumerId,
            long lockDuration,
            long credits,
            DirectBuffer taskType)
    {
        final AdhocTaskSubscription subscription = adhocSubscriptionPool.poll();

        if (subscription == null)
        {
            System.err.println("Cannot open adhoc subscription. No more pooled subscriptions available");
            return subscription;
        }
        else
        {
            subscription.wrap(response);

            subscription.setConsumerId(consumerId);
            subscription.setDefaultCredits(credits);
            subscription.setCredits(credits);
            subscription.setLockDuration(lockDuration);
            subscription.setTaskType(taskType, 0, taskType.capacity());

            addTaskSubscription(subscription);
        }

        return subscription;
    }

    public void removeSubscription(TaskSubscription subscription)
    {
        taskSubscriptions.remove(subscription.getId());

        final TaskSubscription next = subscription.getNext();
        final TaskSubscription previous = subscription.getPrevious();
        previous.setNext(next);
        next.setPrevious(previous);

        final int taskTypeHash = subscription.getTaskTypeHash();
        if (subscriptionsByType.get(taskTypeHash) == subscription)
        {
            if (next != subscription)
            {
                subscriptionsByType.put(taskTypeHash, next);
            }
            else
            {
                subscriptionsByType.remove(taskTypeHash);
            }
        }

        if (subscription instanceof AdhocTaskSubscription)
        {
            adhocSubscriptionPool.add((AdhocTaskSubscription) subscription);
        }
    }

    public TaskSubscription getSubscription(long subscriptionId)
    {
        return taskSubscriptions.get(subscriptionId);
    }
}

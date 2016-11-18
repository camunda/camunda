package org.camunda.tngp.broker.taskqueue.subscription;

import java.util.Queue;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap.KeyIterator;
import org.agrona.collections.Long2ObjectHashMap.KeySet;
import org.agrona.collections.LongHashSet;
import org.agrona.collections.LongIterator;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
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
import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.TransportChannelListener;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.singlemessage.DataFramePool;
import org.camunda.tngp.util.BoundedArrayQueue;

public class LockTasksOperator implements Consumer<Consumer<LockTasksOperator>>, TransportChannelListener
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
    protected LongHashSet taskTypeQuery = new LongHashSet(-1L);

    protected TaskInstanceWriter taskInstanceWriter = new TaskInstanceWriter();

    protected Long2ObjectHashMap<TaskSubscription> taskSubscriptions = new Long2ObjectHashMap<>();
    protected Long2LongHashMap pendingLockedTasks = new Long2LongHashMap(MISSING_VALUE);
    protected Int2ObjectHashMap<TaskSubscription> subscriptionsByType = new Int2ObjectHashMap<>();

    protected DataFramePool dataFramePool;

    protected LockedTaskWriter taskWriter = new LockedTaskWriter();

    protected Queue<AdhocTaskSubscription> adhocSubscriptionPool;

    protected LongHashSet removalCandidates = new LongHashSet(-1L);
    protected ManyToOneConcurrentArrayQueue<Consumer<LockTasksOperator>> cmdQueue = new ManyToOneConcurrentArrayQueue<>(32);

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

    public int doWork()
    {
        int workCount = 0;
        workCount += manageSubscriptions();
        workCount += lockTasks();
        return workCount;
    }

    protected int manageSubscriptions()
    {
        return cmdQueue.drain(this);
    }

    @Override
    public void accept(Consumer<LockTasksOperator> t)
    {
        t.accept(this);
    }

    protected int lockTasks()
    {

        // determine scan start position
        final KeySet keySet = taskSubscriptions.keySet();
        long scanStartPosition = Long.MAX_VALUE;
        KeyIterator subscriptionIdIt = keySet.iterator();

        // determines task types to scan for
        taskTypeQuery.clear();

        while (subscriptionIdIt.hasNext())
        {
            final TaskSubscription subscription = taskSubscriptions.get(subscriptionIdIt.nextLong());

            if (subscription.getCredits() > 0L)
            {
                taskTypeQuery.add(subscription.getTaskTypeHash());

                final DirectBuffer taskType = subscription.getTaskType();
                final long taskTypePosition = taskTypePositionIndex.get(taskType, 0, taskType.capacity(), -1);

                if (taskTypePosition >= 0 && taskTypePosition < scanStartPosition)
                {
                    scanStartPosition = taskTypePosition;
                }
            }
        }

        if (scanStartPosition == Long.MAX_VALUE)
        {
            scanStartPosition = 0;
        }

        // scan for tasks
        taskFinder.init(scanStartPosition, taskTypeQuery);

        int numTasksFound = 0;

        while (!taskTypeQuery.isEmpty() && taskFinder.findNextLockableTask())
        {
            final TaskInstanceReader task = taskFinder.getLockableTask();
            final DirectBuffer foundTaskType = task.getTaskType();
            final long taskTypeBeginIndex = taskTypePositionIndex.get(foundTaskType, 0, foundTaskType.capacity(), -1L);

            if (taskFinder.getLockableTaskPosition() < taskTypeBeginIndex)
            {
                // ignore task for which the index says that it has already been locked
                continue;
            }

            final int taskTypeHash = (int) task.taskTypeHash();
            final TaskSubscription headSubscription = subscriptionsByType.get(taskTypeHash);
            TaskSubscription candidateSubscription = headSubscription;

            boolean candidateFound = false;
            boolean creditsAvailable = false;

            do
            {
                final DirectBuffer taskType = candidateSubscription.getTaskType();

                if (LockableTaskFinder.taskTypeEqual(taskType, foundTaskType))
                {
                    candidateFound = true;
                    creditsAvailable = candidateSubscription.getCredits() > 0L;
                }
                else
                {
                    candidateSubscription = candidateSubscription.getNext();
                }
            }
            while (!(candidateFound && creditsAvailable) && candidateSubscription != headSubscription);

            if (candidateFound)
            {
                if (creditsAvailable)
                {
                    lockTask(
                            candidateSubscription,
                            taskFinder.getLockableTask(),
                            taskFinder.getLockableTaskPosition(),
                            logWriter);
                    numTasksFound++;
                    candidateSubscription.setCredits(candidateSubscription.getCredits() - 1);
                }
                else
                {
                    // stop searching for tasks, if no matching subscription has credits
                    taskTypeQuery.remove(taskTypeHash);
                }
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

        final DirectBuffer payload = task.getPayload();
        taskWriter.id(taskId)
            .lockTime(task.lockTime())
            .workflowInstanceId(task.wfInstanceId())
            .payload(payload, 0, payload.capacity());

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

    public void removeSubscriptionsForChannel(int channelId)
    {
        final KeySet subscriptionIds = taskSubscriptions.keySet();

        final KeyIterator subscriptionIdIt = subscriptionIds.iterator();

        while (subscriptionIdIt.hasNext())
        {
            final long subscriptionId = subscriptionIdIt.nextLong();
            final TaskSubscription subscription = taskSubscriptions.get(subscriptionId);
            if (subscription.getChannelId() == channelId)
            {
                removalCandidates.add(subscriptionId);
            }
        }

        final LongIterator removalIt = removalCandidates.iterator();

        while (removalIt.hasNext())
        {
            final long subscriptionId = removalIt.nextValue();
            removeSubscription(taskSubscriptions.get(subscriptionId));
        }

        removalCandidates.clear();
    }

    @Override
    public void onChannelClosed(TransportChannel channel)
    {
        // invoked in context of transport conductor thread, so we can't handle this synchronously
        cmdQueue.add((t) -> t.removeSubscriptionsForChannel(channel.getId()));
    }
}

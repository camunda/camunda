package org.camunda.tngp.client.task.impl;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.agrona.concurrent.Agent;
import org.camunda.tngp.client.cmd.LockedTask;
import org.camunda.tngp.client.cmd.LockedTasksBatch;
import org.camunda.tngp.client.impl.TngpClientImpl;

public class TaskAcquisition implements Agent, Consumer<AcquisitionCmd>
{
    public static final String ROLE_NAME = "task-acquisition";

    protected final TngpClientImpl client;
    protected final TaskSubscriptions taskSubscriptions;
    protected CommandQueue<AcquisitionCmd> cmdQueue;

    public TaskAcquisition(TngpClientImpl client, TaskSubscriptions subscriptions)
    {
        this.client = client;
        this.taskSubscriptions = subscriptions;
        this.cmdQueue = new DeferredCommandQueue<>(this);
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += evaluateCommands();
        workCount += acquireTasksForSubscriptions();

        return workCount;
    }

    public int evaluateCommands()
    {
        return cmdQueue.drain();
    }

    public int acquireTasksForSubscriptions()
    {
        int workCount = 0;

        workCount += workOnSubscriptions(taskSubscriptions.getManagedExecutionSubscriptions());
        workCount += workOnSubscriptions(taskSubscriptions.getPollableSubscriptions());

        return workCount;
    }

    protected int workOnSubscriptions(Collection<TaskSubscriptionImpl> subscriptions)
    {
        int workCount = 0;
        for (TaskSubscriptionImpl subscription : subscriptions)
        {
            if (subscription.isOpen())
            {
                workCount += acquireTasks(subscription);
            }
            else if (subscription.isClosing() && !subscription.hasQueuedTasks())
            {
                subscription.doClose();
                taskSubscriptions.removeSubscription(subscription);
                workCount++;
            }
        }

        return workCount;
    }

    @Override
    public String roleName()
    {
        return ROLE_NAME;
    }

    @Override
    public void accept(AcquisitionCmd t)
    {
        t.execute(this);
    }

    public void openSubscription(TaskSubscriptionImpl subscription)
    {
        if (subscription.isManagedSubscription())
        {
            taskSubscriptions.addManagedExecutionSubscription(subscription);
        }
        else
        {
            taskSubscriptions.addPollableSubscription(subscription);
        }

        subscription.doOpen();
    }

    protected int acquireTasks(TaskSubscriptionImpl subscription)
    {
        final int remainingCapacity = subscription.capacity() - subscription.size();

        if (remainingCapacity <= 0)
        {
            return 0;
        }

        final int tasksToAcquire = Math.min(remainingCapacity, subscription.getMaxTasks());

        final LockedTasksBatch tasksBatch = client.pollAndLock()
                .taskQueueId(subscription.getTaskQueueId())
                .lockTime(subscription.getLockTime())
                .maxTasks(tasksToAcquire)
                .taskType(subscription.getTaskType())
                .execute();

        final List<LockedTask> lockedTasks = tasksBatch.getLockedTasks();

        for (int i = 0; i < lockedTasks.size(); i++)
        {
            final LockedTask lockedTask = lockedTasks.get(i);

            final TaskImpl task = new TaskImpl(
                    client,
                    lockedTask.getId(),
                    lockedTask.getWorkflowInstanceId(),
                    subscription.getTaskType(),
                    tasksBatch.getLockTime(),
                    subscription.getTaskQueueId());

            subscription.addTask(task); // cannot fail when there is a single thread that adds to the queue
        }

        return lockedTasks.size();
    }

    public void scheduleCommand(AcquisitionCmd command)
    {
        cmdQueue.add(command);
    }

}

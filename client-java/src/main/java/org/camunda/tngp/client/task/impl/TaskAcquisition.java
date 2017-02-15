package org.camunda.tngp.client.task.impl;

import java.util.Collection;
import java.util.function.Consumer;

import org.agrona.concurrent.Agent;
import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.impl.cmd.CreateTaskSubscriptionCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEvent;
import org.camunda.tngp.client.task.impl.TaskDataFrameCollector.SubscribedTaskHandler;

public class TaskAcquisition implements Agent, Consumer<AcquisitionCmd>, SubscribedTaskHandler
{
    public static final String ROLE_NAME = "task-acquisition";

    protected final TngpClientImpl client;
    protected final TaskSubscriptions taskSubscriptions;
    protected CommandQueue<AcquisitionCmd> cmdQueue;
    protected TaskDataFrameCollector taskCollector;

    public TaskAcquisition(TngpClientImpl client, TaskSubscriptions subscriptions, TaskDataFrameCollector taskCollector)
    {
        this.client = client;
        this.taskSubscriptions = subscriptions;
        this.cmdQueue = new DeferredCommandQueue<>(this);
        this.taskCollector = taskCollector;
        this.taskCollector.setTaskHandler(this);
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += evaluateCommands();
        workCount += taskCollector.doWork();
        workCount += manageSubscriptions();

        return workCount;
    }

    public int evaluateCommands()
    {
        return cmdQueue.drain();
    }

    public int manageSubscriptions()
    {
        int workCount = 0;

        workCount += manageSubscriptions(taskSubscriptions.getManagedExecutionSubscriptions());
        workCount += manageSubscriptions(taskSubscriptions.getPollableSubscriptions());

        return workCount;
    }

    protected int manageSubscriptions(Collection<TaskSubscriptionImpl> subscriptions)
    {
        int workCount = 0;

        for (TaskSubscriptionImpl subscription : subscriptions)
        {
            if (subscription.isClosing() && !subscription.hasQueuedTasks())
            {
                closeSubscription(subscription);
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

    public void closeSubscription(TaskSubscriptionImpl subscription)
    {
        client.closeBrokerTaskSubscription()
            .subscriptionId(subscription.getId())
            .topicId(subscription.getTopicId())
            .taskType(subscription.getTaskType())
            .execute();

        subscription.doClose();
        taskSubscriptions.removeSubscription(subscription);
    }

    public void openSubscription(TaskSubscriptionImpl subscription)
    {
        final CreateTaskSubscriptionCmdImpl cmd = client.brokerTaskSubscription();
        cmd
            .topicId(subscription.getTopicId())
            .taskType(subscription.getTaskType())
            .lockDuration(subscription.getLockTime())
            .lockOwner(subscription.getLockOwner())
            .initialCredits(subscription.capacity());

        final long subscriptionId = cmd.execute();
        subscription.setId(subscriptionId);

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

    public void updateCredits(TaskSubscriptionImpl subscription, int credits)
    {
        if (subscription.isOpen())
        {
            client.updateSubscriptionCredits()
                .subscriptionId(subscription.getId())
                .topicId(subscription.getTopicId())
                .taskType(subscription.getTaskType())
                .credits(credits)
                .execute();
        }
    }

    public void scheduleCommand(AcquisitionCmd command)
    {
        cmdQueue.add(command);
    }

    @Override
    public void onTask(long subscriptionId, long key, TaskEvent taskEvent)
    {
        final TaskSubscriptionImpl subscription = taskSubscriptions.getSubscription(subscriptionId);

        if (subscription != null && subscription.isOpen())
        {
            final TaskImpl task = new TaskImpl(
                    client,
                    subscription,
                    key,
                    taskEvent);

            subscription.addTask(task);
        }
    }

}

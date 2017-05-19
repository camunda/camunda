package org.camunda.tngp.client;

import org.camunda.tngp.client.event.TaskTopicSubscriptionBuilder;
import org.camunda.tngp.client.task.PollableTaskSubscription;
import org.camunda.tngp.client.task.PollableTaskSubscriptionBuilder;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;
import org.camunda.tngp.client.task.cmd.CompleteTaskCmd;
import org.camunda.tngp.client.task.cmd.CreateTaskCmd;
import org.camunda.tngp.client.task.cmd.FailTaskCmd;
import org.camunda.tngp.client.task.cmd.PollAndLockAsyncTasksCmd;
import org.camunda.tngp.client.task.cmd.UpdateTaskRetriesCmd;

/**
 * The task client api.
 */
public interface TaskTopicClient
{
    /**
     * Create a new task.
     */
    CreateTaskCmd create();

    /**
     * Complete a locked task.
     */
    CompleteTaskCmd complete();

    /**
     * Mark a locked task as failed.
     */
    FailTaskCmd fail();

    /**
     * Update the remaining retries of a task.
     */
    UpdateTaskRetriesCmd updateRetries();

    /**
     * Create a new subscription to lock tasks and execute them by the given
     * handler.
     */
    TaskSubscriptionBuilder newTaskSubscription();

    /**
     * Create a new subscription to lock tasks. Use
     * {@linkplain PollableTaskSubscription#poll(org.camunda.tngp.client.task.TaskHandler)}
     * to execute the locked tasks.
     */
    PollableTaskSubscriptionBuilder newPollableTaskSubscription();

    /**
     * @return a builder for an event subscription on a task topic that allows
     *         registering specific event handlers for well known task events.
     *         Handler invocation is managed.
     */
    TaskTopicSubscriptionBuilder newSubscription();

    PollAndLockAsyncTasksCmd pollAndLock();

}

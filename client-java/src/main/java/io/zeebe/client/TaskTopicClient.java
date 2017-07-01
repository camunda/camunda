package io.zeebe.client;

import io.zeebe.client.task.PollableTaskSubscription;
import io.zeebe.client.task.PollableTaskSubscriptionBuilder;
import io.zeebe.client.task.TaskSubscriptionBuilder;
import io.zeebe.client.task.cmd.CompleteTaskCmd;
import io.zeebe.client.task.cmd.CreateTaskCmd;
import io.zeebe.client.task.cmd.FailTaskCmd;
import io.zeebe.client.task.cmd.UpdateTaskRetriesCmd;

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
     * {@linkplain PollableTaskSubscription#poll(io.zeebe.client.task.TaskHandler)}
     * to execute the locked tasks.
     */
    PollableTaskSubscriptionBuilder newPollableTaskSubscription();

}

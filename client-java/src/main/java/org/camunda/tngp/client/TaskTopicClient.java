package org.camunda.tngp.client;

import org.camunda.tngp.client.cmd.CompleteTaskCmd;
import org.camunda.tngp.client.cmd.CreateTaskCmd;
import org.camunda.tngp.client.cmd.FailTaskCmd;
import org.camunda.tngp.client.cmd.PollAndLockAsyncTasksCmd;
import org.camunda.tngp.client.cmd.UpdateTaskRetriesCmd;
import org.camunda.tngp.client.event.TaskTopicSubscriptionBuilder;
import org.camunda.tngp.client.task.PollableTaskSubscriptionBuilder;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;

public interface TaskTopicClient
{
    CreateTaskCmd create();

    PollAndLockAsyncTasksCmd pollAndLock();

    CompleteTaskCmd complete();

    FailTaskCmd fail();

    UpdateTaskRetriesCmd updateRetries();

    TaskSubscriptionBuilder newTaskSubscription();

    PollableTaskSubscriptionBuilder newPollableTaskSubscription();

    /**
     * @return a builder for an event subscription on a task topic that allows registering
     *   specific event handlers for well known task events. Handler invocation is managed.
     */
    TaskTopicSubscriptionBuilder newSubscription();
}

package org.camunda.tngp.client;

import org.camunda.tngp.client.cmd.CompleteAsyncTaskCmd;
import org.camunda.tngp.client.cmd.CreateAsyncTaskCmd;
import org.camunda.tngp.client.cmd.FailAsyncTaskCmd;
import org.camunda.tngp.client.cmd.PollAndLockAsyncTasksCmd;
import org.camunda.tngp.client.event.TaskTopicSubscriptionBuilder;
import org.camunda.tngp.client.task.PollableTaskSubscriptionBuilder;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;

public interface TaskTopicClient
{
    CreateAsyncTaskCmd create();

    PollAndLockAsyncTasksCmd pollAndLock();

    CompleteAsyncTaskCmd complete();

    FailAsyncTaskCmd fail();

    TaskSubscriptionBuilder newTaskSubscription();

    PollableTaskSubscriptionBuilder newPollableTaskSubscription();

    /**
     * @return a builder for an event subscription on a task topic that allows registering
     *   specific event handlers for well known task events. Handler invocation is managed.
     */
    TaskTopicSubscriptionBuilder newSubscription();
}

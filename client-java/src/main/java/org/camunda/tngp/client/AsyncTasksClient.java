package org.camunda.tngp.client;

import org.camunda.tngp.client.cmd.CompleteAsyncTaskCmd;
import org.camunda.tngp.client.cmd.CreateAsyncTaskCmd;
import org.camunda.tngp.client.cmd.PollAndLockAsyncTasksCmd;
import org.camunda.tngp.client.task.PollableTaskSubscriptionBuilder;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;

public interface AsyncTasksClient
{
    CreateAsyncTaskCmd create();

    PollAndLockAsyncTasksCmd pollAndLock();

    CompleteAsyncTaskCmd complete();

    TaskSubscriptionBuilder newSubscription();

    PollableTaskSubscriptionBuilder newPollableSubscription();
}

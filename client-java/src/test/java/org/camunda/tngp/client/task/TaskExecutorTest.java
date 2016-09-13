package org.camunda.tngp.client.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.tngp.client.task.impl.TaskExecutor;
import org.camunda.tngp.client.task.impl.TaskSubscriptionImpl;
import org.camunda.tngp.client.task.impl.TaskSubscriptions;
import org.junit.Test;

public class TaskExecutorTest
{

    @Test
    public void shouldExecuteTasks() throws Exception
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();

        final TaskSubscriptionImpl subscription = mock(TaskSubscriptionImpl.class);
        when(subscription.poll()).thenReturn(34);
        subscriptions.addManagedExecutionSubscription(subscription);

        final TaskExecutor executor = new TaskExecutor(subscriptions);

        // when
        final int workCount = executor.doWork();

        // then
        assertThat(workCount).isEqualTo(34);

        verify(subscription).poll();

    }
}

package org.camunda.tngp.client.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.tngp.client.task.impl.SubscriptionExecutor;
import org.camunda.tngp.client.task.impl.TaskSubscriptionImpl;
import org.camunda.tngp.client.task.impl.EventSubscriptions;
import org.junit.Test;

public class TaskExecutorTest
{
    @Test
    public void shouldExecuteTasks() throws Exception
    {
        // given
        final EventSubscriptions<TaskSubscriptionImpl> subscriptions = new EventSubscriptions<>();

        final TaskSubscriptionImpl subscription = mock(TaskSubscriptionImpl.class);
        when(subscription.isManagedSubscription()).thenReturn(true);
        when(subscription.poll()).thenReturn(34);
        subscriptions.addSubscription(subscription);

        final SubscriptionExecutor executor = new SubscriptionExecutor(subscriptions);

        // when
        final int workCount = executor.doWork();

        // then
        assertThat(workCount).isEqualTo(34);

        verify(subscription).poll();
    }
}

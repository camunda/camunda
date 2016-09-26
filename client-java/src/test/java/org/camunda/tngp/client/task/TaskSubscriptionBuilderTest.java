package org.camunda.tngp.client.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.task.impl.PollableTaskSubscriptionBuilderImpl;
import org.camunda.tngp.client.task.impl.TaskAcquisition;
import org.camunda.tngp.client.task.impl.TaskSubscriptionBuilderImpl;
import org.camunda.tngp.client.task.impl.TaskSubscriptionImpl;
import org.camunda.tngp.client.task.impl.TaskSubscriptions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TaskSubscriptionBuilderTest
{

    protected TaskSubscriptions subscriptions;
    protected TaskAcquisition acquisition;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp()
    {
        subscriptions = new TaskSubscriptions();
        acquisition = new TaskAcquisition(mock(TngpClientImpl.class), subscriptions)
        {
            {
                this.cmdQueue = new ImmediateCommandQueue<>(this);
            }
        };
    }

    @Test
    public void shouldBuildSubscription()
    {
        // given
        final TaskSubscriptionBuilder builder = new TaskSubscriptionBuilderImpl(acquisition, true);

        final TaskHandler handler = mock(TaskHandler.class);
        builder
            .handler(handler)
            .lockTime(654L)
            .taskQueueId(123)
            .taskType("fooo");

        // when
        final TaskSubscription taskSubscription = builder.open();

        // then
        assertThat(taskSubscription instanceof TaskSubscriptionImpl);

        final TaskSubscriptionImpl subscriptionImpl = (TaskSubscriptionImpl) taskSubscription;
        assertThat(subscriptionImpl.getLockTime()).isEqualTo(654L);
        assertThat(subscriptionImpl.getMaxTasks()).isEqualTo(1);
        assertThat(subscriptionImpl.getTaskQueueId()).isEqualTo(123);
        assertThat(subscriptionImpl.getTaskType()).isEqualTo("fooo");

        assertThat(subscriptions.getManagedExecutionSubscriptions()).contains(subscriptionImpl);
    }

    @Test
    public void shouldBuildPollableSubscription()
    {
        // given
        final PollableTaskSubscriptionBuilder builder = new PollableTaskSubscriptionBuilderImpl(acquisition, true);

        builder
            .lockTime(654L)
            .taskQueueId(123)
            .taskType("fooo");

        // when
        final PollableTaskSubscription taskSubscription = builder.open();

        // then
        assertThat(taskSubscription instanceof TaskSubscriptionImpl);

        final TaskSubscriptionImpl subscriptionImpl = (TaskSubscriptionImpl) taskSubscription;
        assertThat(subscriptionImpl.getLockTime()).isEqualTo(654L);
        assertThat(subscriptionImpl.getMaxTasks()).isEqualTo(1);
        assertThat(subscriptionImpl.getTaskQueueId()).isEqualTo(123);
        assertThat(subscriptionImpl.getTaskType()).isEqualTo("fooo");

        assertThat(subscriptions.getPollableSubscriptions()).contains(subscriptionImpl);
    }

    @Test
    public void shouldValidateMissingTaskType()
    {
        // given
        final PollableTaskSubscriptionBuilder builder = new PollableTaskSubscriptionBuilderImpl(acquisition, true);

        builder
            .lockTime(654L)
            .taskQueueId(123);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("taskType must not be null");

        // when
        builder.open();
    }

    @Test
    public void shouldValidateMissingTaskHandler()
    {
        // given
        final TaskSubscriptionBuilder builder = new TaskSubscriptionBuilderImpl(acquisition, true);

        builder
            .lockTime(654L)
            .taskQueueId(123)
            .taskType("foo");

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("taskHandler must not be null");

        // when
        builder.open();
    }

    @Test
    public void shouldValidateLockTime()
    {
        // given
        final TaskSubscriptionBuilder builder = new TaskSubscriptionBuilderImpl(acquisition, true);

        builder
            .lockTime(0L)
            .taskQueueId(123)
            .taskType("foo")
            .handler((t) ->
            { });

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("lockTime must be greater than 0");

        // when
        builder.open();
    }

    @Test
    public void shouldSetLockTimeWithTimeUnit()
    {
        // given
        final TaskSubscriptionBuilder builder = new TaskSubscriptionBuilderImpl(acquisition, true);

        builder
            .handler(mock(TaskHandler.class))
            .lockTime(Duration.ofDays(10))
            .taskQueueId(123)
            .taskType("fooo");

        // when
        final TaskSubscription taskSubscription = builder.open();

        // then
        final TaskSubscriptionImpl subscriptionImpl = (TaskSubscriptionImpl) taskSubscription;

        assertThat(subscriptionImpl.getLockTime()).isEqualTo(TimeUnit.DAYS.toMillis(10L));
    }

    @Test
    public void shouldSetLockTimeWithTimeUnitForPollableSubscription()
    {
        // given
        final PollableTaskSubscriptionBuilder builder = new PollableTaskSubscriptionBuilderImpl(acquisition, true);

        builder
            .lockTime(Duration.ofDays(10))
            .taskQueueId(123)
            .taskType("fooo");

        // when
        final PollableTaskSubscription taskSubscription = builder.open();

        // then
        final TaskSubscriptionImpl subscriptionImpl = (TaskSubscriptionImpl) taskSubscription;

        assertThat(subscriptionImpl.getLockTime()).isEqualTo(TimeUnit.DAYS.toMillis(10L));
    }
}

package org.camunda.tngp.client.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.CreateTaskSubscriptionCmdImpl;
import org.camunda.tngp.client.task.impl.PollableTaskSubscriptionBuilderImpl;
import org.camunda.tngp.client.task.impl.TaskAcquisition;
import org.camunda.tngp.client.task.impl.TaskDataFrameCollector;
import org.camunda.tngp.client.task.impl.TaskSubscriptionBuilderImpl;
import org.camunda.tngp.client.task.impl.TaskSubscriptionImpl;
import org.camunda.tngp.client.task.impl.TaskSubscriptions;
import org.camunda.tngp.test.util.FluentMock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskSubscriptionBuilderTest
{

    protected TaskSubscriptions subscriptions;
    protected TaskAcquisition acquisition;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Mock
    protected TngpClientImpl client;

    @FluentMock
    protected CreateTaskSubscriptionCmdImpl openSubscriptionCmd;



    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        when(client.brokerTaskSubscription()).thenReturn(openSubscriptionCmd);
        when(openSubscriptionCmd.execute()).thenReturn(123L);

        subscriptions = new TaskSubscriptions();
        acquisition = new TaskAcquisition(client, subscriptions, mock(TaskDataFrameCollector.class))
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
            .lockOwner(2)
            .topicId(123)
            .taskType("fooo");

        // when
        final TaskSubscription taskSubscription = builder.open();

        // then
        assertThat(taskSubscription instanceof TaskSubscriptionImpl);

        final TaskSubscriptionImpl subscriptionImpl = (TaskSubscriptionImpl) taskSubscription;
        assertThat(subscriptionImpl.getLockTime()).isEqualTo(654L);
        assertThat(subscriptionImpl.capacity()).isEqualTo(TaskSubscriptionBuilderImpl.DEFAULT_TASK_FETCH_SIZE);
        assertThat(subscriptionImpl.getTopicId()).isEqualTo(123);
        assertThat(subscriptionImpl.getTaskType()).isEqualTo("fooo");

        assertThat(subscriptions.getManagedExecutionSubscriptions()).contains(subscriptionImpl);

        verify(client).brokerTaskSubscription();
        verify(openSubscriptionCmd).lockOwner(2);
        verify(openSubscriptionCmd).lockDuration(654L);
        verify(openSubscriptionCmd).taskType("fooo");
        verify(openSubscriptionCmd).execute();
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
        assertThat(subscriptionImpl.capacity()).isEqualTo(TaskSubscriptionBuilderImpl.DEFAULT_TASK_FETCH_SIZE);
        assertThat(subscriptionImpl.getTopicId()).isEqualTo(123);
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
            .lockOwner(2)
            .topicId(123)
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
            .lockOwner(2)
            .topicId(123)
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
            .lockOwner(2)
            .topicId(123)
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

    @Test
    public void shouldThrowExceptionWhenSubscriptionCannotBeOpened()
    {
        // given
        final TaskSubscriptionBuilder builder = new TaskSubscriptionBuilderImpl(acquisition, true);

        final TaskHandler handler = mock(TaskHandler.class);
        builder
            .handler(handler)
            .lockTime(654L)
            .lockOwner(2)
            .topicId(123)
            .taskType("fooo");

        when(openSubscriptionCmd.execute()).thenThrow(new RuntimeException("foo"));

        try
        {
            // when
            builder.open();
            fail("expected exception");
        }
        catch (RuntimeException e)
        {
            // then
            assertThat(e).hasMessageContaining("Could not open subscription");
        }

        assertThat(subscriptions.getManagedExecutionSubscriptions()).isEmpty();
    }

}

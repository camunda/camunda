package org.camunda.tngp.client.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.test.util.FluentMock;
import org.camunda.tngp.client.cmd.CompleteAsyncTaskCmd;
import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.impl.cmd.CloseTaskSubscriptionCmdImpl;
import org.camunda.tngp.client.impl.cmd.CreateTaskSubscriptionCmdImpl;
import org.camunda.tngp.client.impl.cmd.ProvideSubscriptionCreditsCmdImpl;
import org.camunda.tngp.client.task.impl.TaskAcquisition;
import org.camunda.tngp.client.task.impl.TaskDataFrameCollector;
import org.camunda.tngp.client.task.impl.TaskImpl;
import org.camunda.tngp.client.task.impl.TaskSubscriptionImpl;
import org.camunda.tngp.client.task.impl.TaskSubscriptions;
import org.camunda.tngp.protocol.taskqueue.SubscribedTaskReader;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class TaskSubscriptionTest
{
    public static final long SUBSCRIPTION_ID = 123L;

    @Mock
    protected TngpClientImpl client;

    @FluentMock
    protected CreateTaskSubscriptionCmdImpl createSubscriptionCmd;

    @FluentMock
    protected CloseTaskSubscriptionCmdImpl closeSubscriptionCmd;

    @FluentMock
    protected ProvideSubscriptionCreditsCmdImpl provideCreditsCmd;

    @FluentMock
    protected CompleteAsyncTaskCmd completeCmd;

    @Mock
    protected TaskHandler taskHandler;

    @Mock
    protected TaskDataFrameCollector taskCollector;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        when(client.brokerTaskSubscription()).thenReturn(createSubscriptionCmd);
        when(createSubscriptionCmd.execute()).thenReturn(SUBSCRIPTION_ID);
        when(client.closeBrokerTaskSubscription()).thenReturn(closeSubscriptionCmd);
        when(client.complete()).thenReturn(completeCmd);
        when(client.provideSubscriptionCredits()).thenReturn(provideCreditsCmd);
    }

    @Test
    public void shouldOpenManagedExecutionSubscription() throws Exception
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, true);

        // when
        subscription.openAsync();
        final int workCount = acquisition.evaluateCommands();

        // then
        assertThat(workCount).isEqualTo(1);

        assertThat(subscription.isOpen()).isTrue();
        assertThat(subscriptions.getManagedExecutionSubscriptions()).containsExactly(subscription);
        assertThat(subscriptions.getPollableSubscriptions()).isEmpty();
    }

    @Test
    public void shouldCloseManagedExecutionSubscription()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        // when
        subscription.closeAsnyc();

        // then the subscription is staged as closing
        assertThat(subscription.isOpen()).isFalse();
        assertThat(subscription.isClosing()).isTrue();
        assertThat(subscription.isClosed()).isFalse();
        assertThat(subscriptions.getManagedExecutionSubscriptions()).isNotEmpty();

        // and closed on the next acquisition cycle
        final int workCount = acquisition.manageSubscriptions();

        assertThat(workCount).isEqualTo(1);

        assertThat(subscription.isOpen()).isFalse();
        assertThat(subscription.isClosing()).isFalse();
        assertThat(subscription.isClosed()).isTrue();
        assertThat(subscriptions.getManagedExecutionSubscriptions()).isEmpty();

        verify(client).closeBrokerTaskSubscription();
        verify(closeSubscriptionCmd).subscriptionId(SUBSCRIPTION_ID);
        verify(closeSubscriptionCmd).consumerId((short) 0);
        verify(closeSubscriptionCmd).execute();
    }

    @Test
    public void shouldAcquireTasksInWorkLoop() throws Exception
    {
        // given
        final TaskDataFrameCollector taskCollector = mock(TaskDataFrameCollector.class);
        when(taskCollector.doWork()).thenReturn(32);

        final TaskAcquisition acquisition = new TaskAcquisition(client, new TaskSubscriptions(), taskCollector);

        // when
        final int result = acquisition.doWork();

        // then
        assertThat(result).isEqualTo(32);
        verify(taskCollector).doWork();
    }

    @Test
    public void shouldInvokeHandlerOnPoll()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        // two subscribed tasks
        acquisition.onTask(task(1));
        acquisition.onTask(task(2));

        // when
        final int workCount = subscription.poll();

        // then
        assertThat(workCount).isEqualTo(2);

        verify(taskHandler).handle(argThat(hasId(1)));
        verify(taskHandler).handle(argThat(hasId(2)));
        verifyNoMoreInteractions(taskHandler);
    }

    @Test
    public void shouldAutoCompleteTask()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        acquisition.onTask(task(1));

        // when
        subscription.poll();

        // then
        verify(client).complete();
        verify(completeCmd).taskId(1);
        verify(completeCmd).execute();
    }

    @Test
    public void shouldNotAutoCompleteTask()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, false);

        subscription.openAsync();
        acquisition.evaluateCommands();

        acquisition.onTask(task(1));

        // when
        subscription.poll();

        // then
        verify(client, never()).complete();
        verifyZeroInteractions(completeCmd);
    }

    @Test
    public void shouldNotAutoCompleteTaskOnException()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);
        doThrow(new RuntimeException()).when(taskHandler).handle(any());

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        acquisition.onTask(task(1));

        // when
        try
        {
            subscription.poll();
        }
        catch (Exception e)
        {
           // expected
        }

        // then
        verify(client, never()).complete();
        verify(completeCmd, never()).execute();
    }


    @Test
    public void shouldDistributeTasks()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        // when
        acquisition.onTask(task(1));
        acquisition.onTask(task(2));
        acquisition.onTask(task(3));

        // then
        assertThat(subscription.size()).isEqualTo(3);
    }


    @Test
    public void shouldDistributeWithTwoSubscriptionsForSameType()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription1 = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, true);
        final TaskSubscriptionImpl subscription2 = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, true);

        subscription1.openAsync();
        subscription2.openAsync();
        acquisition.evaluateCommands();

        // when
        acquisition.onTask(task(1));

        // then
        assertThat(subscription1.size() + subscription2.size()).isEqualTo(1);
    }

    @Test
    public void shouldNotDistributeMoreThanSubscriptionCapacity()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        for (int i = 0; i < subscription.capacity(); i++)
        {
            subscription.addTask(mock(TaskImpl.class));
        }

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Cannot add any more tasks. Task queue saturated.");

        // when
        acquisition.onTask(task(1));
    }

    @Test
    public void shouldOpenPollableSubscription()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl("foo", 0, 123L, 5, acquisition, true);

        // when
        subscription.openAsync();
        final int workCount = acquisition.evaluateCommands();

        // then
        assertThat(workCount).isEqualTo(1);
        assertThat(subscription.isOpen()).isTrue();
        assertThat(subscriptions.getPollableSubscriptions()).containsExactly(subscription);
        assertThat(subscriptions.getManagedExecutionSubscriptions()).isEmpty();
    }

    @Test
    public void shouldPollSubscription()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl("foo", 0, 123L, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        acquisition.onTask(task(1));
        acquisition.onTask(task(2));

        // when
        int workCount = subscription.poll(taskHandler);

        // then
        assertThat(workCount).isEqualTo(2);

        verify(taskHandler).handle(argThat(hasId(1)));
        verify(taskHandler).handle(argThat(hasId(2)));

        // and polling again does not trigger the handler anymore
        workCount = subscription.poll(taskHandler);
        assertThat(workCount).isEqualTo(0);

        verifyNoMoreInteractions(taskHandler);
    }

    @Test
    public void shouldAddCredits()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl("foo", 0, 123L, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        acquisition.onTask(task(1));

        // when
        subscription.poll(taskHandler);
        acquisition.evaluateCommands();

        // then
        final InOrder inOrder = Mockito.inOrder(client, provideCreditsCmd);

        inOrder.verify(client).provideSubscriptionCredits();
        inOrder.verify(provideCreditsCmd).consumerId((short) 0);
        inOrder.verify(provideCreditsCmd).subscriptionId(subscription.getId());
        inOrder.verify(provideCreditsCmd).credits(1);
        inOrder.verify(provideCreditsCmd).execute();
    }

    @Test
    public void shouldPopulateTaskProperties()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl("foo", 0, 123L, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        final SubscribedTaskReader task = task(1);
        when(task.wfInstanceId()).thenReturn(444L);
        final Instant lockTime = Instant.ofEpochMilli(0L).plus(Duration.ofDays(40L));
        when(task.lockTime()).thenReturn(lockTime.toEpochMilli());

        acquisition.onTask(task);

        // when
        subscription.poll(taskHandler);

        // then
        verify(taskHandler).handle(argThat(new ArgumentMatcher<Task>()
        {
            @Override
            public boolean matches(Object argument)
            {
                final Task task = (Task) argument;
                return task.getId() == 1 &&
                        task.getWorkflowInstanceId() == 444L &&
                        lockTime.equals(task.getLockExpirationTime()) &&
                        "foo".equals(task.getType());
            }
        }));
    }

    protected SubscribedTaskReader task(long id)
    {
        final SubscribedTaskReader lockedTask = mock(SubscribedTaskReader.class);
        when(lockedTask.taskId()).thenReturn(id);
        when(lockedTask.subscriptionId()).thenReturn(SUBSCRIPTION_ID);
        when(lockedTask.payload()).thenReturn(new UnsafeBuffer(0, 0));

        return lockedTask;
    }

    protected static ArgumentMatcher<Task> hasId(final long taskId)
    {
        return new ArgumentMatcher<Task>()
        {
            @Override
            public boolean matches(Object argument)
            {
                return argument instanceof Task && ((Task) argument).getId() == taskId;
            }
        };
    }
}

package org.camunda.tngp.client.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.camunda.tngp.broker.test.util.FluentMock;
import org.camunda.tngp.client.cmd.CompleteAsyncTaskCmd;
import org.camunda.tngp.client.cmd.LockedTask;
import org.camunda.tngp.client.cmd.LockedTasksBatch;
import org.camunda.tngp.client.cmd.PollAndLockAsyncTasksCmd;
import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.task.impl.TaskAcquisition;
import org.camunda.tngp.client.task.impl.TaskImpl;
import org.camunda.tngp.client.task.impl.TaskSubscriptionImpl;
import org.camunda.tngp.client.task.impl.TaskSubscriptions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskSubscriptionTest
{
    @Mock
    protected TngpClientImpl client;

    @FluentMock
    protected PollAndLockAsyncTasksCmd pollCmd;

    @FluentMock
    protected CompleteAsyncTaskCmd completeCmd;

    @Mock
    protected TaskHandler taskHandler;

    @Mock
    protected LockedTasksBatch taskBatch;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        when(client.pollAndLock()).thenReturn(pollCmd);
        when(pollCmd.execute()).thenReturn(taskBatch);
        when(client.complete()).thenReturn(completeCmd);
    }

    @Test
    public void shouldOpenManagedExecutionSubscription() throws Exception
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions);

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
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions);

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
        final int workCount = acquisition.acquireTasksForSubscriptions();

        assertThat(workCount).isEqualTo(1);

        assertThat(subscription.isOpen()).isFalse();
        assertThat(subscription.isClosing()).isFalse();
        assertThat(subscription.isClosed()).isTrue();
        assertThat(subscriptions.getManagedExecutionSubscriptions()).isEmpty();
    }

    @Test
    public void shouldInvokeHandlerOnPoll()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        final List<LockedTask> tasks = new ArrayList<>();
        tasks.add(task(1));
        tasks.add(task(2));
        when(taskBatch.getLockedTasks()).thenReturn(tasks);

        acquisition.acquireTasksForSubscriptions();

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
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        final LockedTask task = task(1);
        when(taskBatch.getLockedTasks()).thenReturn(Arrays.asList(task));

        acquisition.acquireTasksForSubscriptions();

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
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, false);

        subscription.openAsync();
        acquisition.evaluateCommands();

        final LockedTask task = task(1);
        when(taskBatch.getLockedTasks()).thenReturn(Arrays.asList(task));

        acquisition.acquireTasksForSubscriptions();

        // when
        subscription.poll();

        // then
        verify(client, never()).complete();
        verifyZeroInteractions(completeCmd);
    }


    @Test
    public void shouldAcquire()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        final List<LockedTask> tasks = Arrays.asList(task(1), task(2), task(3));
        when(taskBatch.getLockedTasks()).thenReturn(tasks);

        // when
        final int workCount = acquisition.acquireTasksForSubscriptions();

        // then
        assertThat(workCount).isEqualTo(3);

        verify(client).pollAndLock();
        verify(pollCmd).lockTime(123L);
        verify(pollCmd).maxTasks(5);
        verify(pollCmd).taskQueueId(0);
        verify(pollCmd).taskType("foo");
        verify(pollCmd).execute();

        verifyNoMoreInteractions(pollCmd, client);

        assertThat(subscription.size()).isEqualTo(3);
    }

    @Test
    public void shouldAcquireWithTwoSubscriptionsForSameType()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions);

        final TaskSubscriptionImpl subscription1 = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, true);
        final TaskSubscriptionImpl subscription2 = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, true);

        subscription1.openAsync();
        subscription2.openAsync();
        acquisition.evaluateCommands();

        final List<LockedTask> tasks = Arrays.asList(task(1));
        when(taskBatch.getLockedTasks()).thenReturn(tasks);

        final LockedTasksBatch taskBatch2 = mock(LockedTasksBatch.class);
        when(taskBatch2.getLockedTasks()).thenReturn(Collections.emptyList());

        when(pollCmd.execute()).thenReturn(taskBatch, taskBatch2);

        // when
        acquisition.acquireTasksForSubscriptions();

        // then
        assertThat(subscription1.size() + subscription2.size()).isEqualTo(1);
    }

    @Test
    public void shouldNotAcquireMoreThanSubscriptionCapacity()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        for (int i = 0; i < TaskSubscriptionImpl.CAPACITY - 1; i++)
        {
            subscription.addTask(mock(TaskImpl.class));
        }

        // when
        acquisition.acquireTasksForSubscriptions();

        // then
        verify(pollCmd).maxTasks(1);
    }

    @Test
    public void shouldNotAcquireWhenSubscriptionFull()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, "foo", 0, 123L, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        for (int i = 0; i < TaskSubscriptionImpl.CAPACITY; i++)
        {
            subscription.addTask(mock(TaskImpl.class));
        }

        // when
        acquisition.acquireTasksForSubscriptions();

        // then
        verifyZeroInteractions(pollCmd);
    }

    @Test
    public void shouldOpenPollableSubscription()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl("foo", 0, 123L, 5, acquisition, true);

        // when
        subscription.openAsync();
        acquisition.evaluateCommands();

        // then
        assertThat(subscription.isOpen()).isTrue();
        assertThat(subscriptions.getPollableSubscriptions()).containsExactly(subscription);
        assertThat(subscriptions.getManagedExecutionSubscriptions()).isEmpty();
    }

    @Test
    public void shouldPollSubscription()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl("foo", 0, 123L, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        final List<LockedTask> tasks = new ArrayList<>();
        tasks.add(task(1));
        tasks.add(task(2));
        when(taskBatch.getLockedTasks()).thenReturn(tasks);

        acquisition.acquireTasksForSubscriptions();

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
    public void shouldPopulateTaskProperties()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl("foo", 0, 123L, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        final List<LockedTask> tasks = new ArrayList<>();
        final LockedTask task = task(1);
        when(task.getWorkflowInstanceId()).thenReturn(444L);
        tasks.add(task);

        when(taskBatch.getLockedTasks()).thenReturn(tasks);

        acquisition.acquireTasksForSubscriptions();

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
                        "foo".equals(task.getType());
            }
        }));
    }

    protected LockedTask task(long id)
    {
        final LockedTask lockedTask = mock(LockedTask.class);
        when(lockedTask.getId()).thenReturn(id);

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

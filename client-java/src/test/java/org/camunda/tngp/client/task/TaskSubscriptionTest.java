/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.client.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.camunda.tngp.broker.test.util.FluentMock;
import org.camunda.tngp.client.cmd.CompleteAsyncTaskCmd;
import org.camunda.tngp.client.cmd.FailAsyncTaskCmd;
import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.CloseTaskSubscriptionCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.CreateTaskSubscriptionCmdImpl;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEvent;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEventType;
import org.camunda.tngp.client.impl.cmd.taskqueue.UpdateSubscriptionCreditsCmdImpl;
import org.camunda.tngp.client.impl.data.MsgPackConverter;
import org.camunda.tngp.client.task.impl.TaskAcquisition;
import org.camunda.tngp.client.task.impl.TaskDataFrameCollector;
import org.camunda.tngp.client.task.impl.TaskImpl;
import org.camunda.tngp.client.task.impl.TaskSubscriptionImpl;
import org.camunda.tngp.client.task.impl.TaskSubscriptions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskSubscriptionTest
{

    public static final long SUBSCRIPTION_ID = 123L;
    private static final int TOPIC_ID = 0;
    private static final String TASK_TYPE = "foo";
    private static final int LOCK_OWNER = 1;
    private static final long LOCK_TIME = 123L;

    private final MsgPackConverter msgPackConverter = new MsgPackConverter();

    @Mock
    protected TngpClientImpl client;

    @FluentMock
    protected CreateTaskSubscriptionCmdImpl createSubscriptionCmd;

    @FluentMock
    protected CloseTaskSubscriptionCmdImpl closeSubscriptionCmd;

    @FluentMock
    protected UpdateSubscriptionCreditsCmdImpl updateCreditsCmd;

    @FluentMock
    protected CompleteAsyncTaskCmd completeCmd;

    @FluentMock
    protected FailAsyncTaskCmd failCmd;

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
        when(client.updateSubscriptionCredits()).thenReturn(updateCreditsCmd);
        when(client.complete()).thenReturn(completeCmd);
        when(client.fail()).thenReturn(failCmd);
    }

    @Test
    public void shouldOpenManagedExecutionSubscription() throws Exception
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, TASK_TYPE, TOPIC_ID, LOCK_TIME, LOCK_OWNER, 5, acquisition, true);

        // when
        subscription.openAsync();
        final int workCount = acquisition.evaluateCommands();

        // then
        assertThat(workCount).isEqualTo(1);

        assertThat(subscription.isOpen()).isTrue();
        assertThat(subscriptions.getManagedExecutionSubscriptions()).containsExactly(subscription);
        assertThat(subscriptions.getPollableSubscriptions()).isEmpty();

        verify(client).brokerTaskSubscription();
        verify(createSubscriptionCmd).topicId(TOPIC_ID);
        verify(createSubscriptionCmd).taskType(TASK_TYPE);
        verify(createSubscriptionCmd).lockDuration(LOCK_TIME);
        verify(createSubscriptionCmd).lockOwner(LOCK_OWNER);
        verify(createSubscriptionCmd).initialCredits(anyInt());
        verify(createSubscriptionCmd).execute();
    }

    @Test
    public void shouldCloseManagedExecutionSubscription()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, TASK_TYPE, TOPIC_ID, LOCK_TIME, LOCK_OWNER, 5, acquisition, true);

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
        verify(closeSubscriptionCmd).topicId(TOPIC_ID);
        verify(closeSubscriptionCmd).taskType(TASK_TYPE);
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
    public void shouldInvokeHandlerOnPoll() throws Exception
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, TASK_TYPE, TOPIC_ID, LOCK_TIME, LOCK_OWNER, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        // two subscribed tasks
        acquisition.onTask(SUBSCRIPTION_ID, 1L, task());
        acquisition.onTask(SUBSCRIPTION_ID, 2L, task());

        // when
        final int workCount = subscription.poll();

        // then
        assertThat(workCount).isEqualTo(2);

        verify(taskHandler).handle(argThat(hasKey(1)));
        verify(taskHandler).handle(argThat(hasKey(2)));
        verifyNoMoreInteractions(taskHandler);
    }

    @Test
    public void shouldAutoCompleteTask()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, TASK_TYPE, TOPIC_ID, LOCK_TIME, LOCK_OWNER, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        final TaskEvent task = task();

        acquisition.onTask(SUBSCRIPTION_ID, 1L, task);

        // when
        subscription.poll();

        // then
        verify(client).complete();
        verify(completeCmd).taskKey(1L);
        verify(completeCmd).topicId(TOPIC_ID);
        verify(completeCmd).taskType(TASK_TYPE);
        verify(completeCmd).lockOwner(LOCK_OWNER);
        verify(completeCmd).headers(task.getHeaders());
        verify(completeCmd).payload(msgPackConverter.convertToJson(task.getPayload()));
        verify(completeCmd).execute();
    }

    @Test
    public void shouldNotAutoCompleteTask()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, TASK_TYPE, TOPIC_ID, LOCK_TIME, LOCK_OWNER, 5, acquisition, false);

        subscription.openAsync();
        acquisition.evaluateCommands();

        acquisition.onTask(SUBSCRIPTION_ID, 1L, task());

        // when
        subscription.poll();

        // then
        verify(client, never()).complete();
        verifyZeroInteractions(completeCmd);
    }

    @Test
    public void shouldMarkTaskAsFailedOnException() throws Exception
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        doThrow(new RuntimeException()).when(taskHandler).handle(any());

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, TASK_TYPE, TOPIC_ID, LOCK_TIME, LOCK_OWNER, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        final TaskEvent task = task();

        acquisition.onTask(SUBSCRIPTION_ID, 1L, task);

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
        verify(client).fail();
        verify(failCmd).taskKey(1L);
        verify(failCmd).topicId(TOPIC_ID);
        verify(failCmd).taskType(TASK_TYPE);
        verify(failCmd).lockOwner(LOCK_OWNER);
        verify(failCmd).headers(task.getHeaders());
        verify(failCmd).payload(msgPackConverter.convertToJson(task.getPayload()));
        verify(failCmd).failure(any(RuntimeException.class));
        verify(failCmd).execute();

        verify(client, never()).complete();
        verify(completeCmd, never()).execute();
    }

    @Test
    public void shouldDistributeTasks()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, TASK_TYPE, TOPIC_ID, LOCK_TIME, LOCK_OWNER, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        // when
        acquisition.onTask(SUBSCRIPTION_ID, 1L, task());
        acquisition.onTask(SUBSCRIPTION_ID, 2L, task());
        acquisition.onTask(SUBSCRIPTION_ID, 3L, task());

        // then
        assertThat(subscription.size()).isEqualTo(3);
    }

    @Test
    public void shouldDistributeWithTwoSubscriptionsForSameType()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription1 = new TaskSubscriptionImpl(taskHandler, TASK_TYPE, TOPIC_ID, LOCK_TIME, LOCK_OWNER, 5, acquisition, true);
        final TaskSubscriptionImpl subscription2 = new TaskSubscriptionImpl(taskHandler, TASK_TYPE, TOPIC_ID, LOCK_TIME, LOCK_OWNER, 5, acquisition, true);

        subscription1.openAsync();
        subscription2.openAsync();
        acquisition.evaluateCommands();

        // when
        acquisition.onTask(SUBSCRIPTION_ID, 1L, task());

        // then
        assertThat(subscription1.size() + subscription2.size()).isEqualTo(1);
    }

    @Test
    public void shouldNotDistributeMoreThanSubscriptionCapacity()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(taskHandler, TASK_TYPE, TOPIC_ID, LOCK_TIME, LOCK_OWNER, 5, acquisition, true);

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
        acquisition.onTask(SUBSCRIPTION_ID, 1L, task());
    }

    @Test
    public void shouldOpenPollableSubscription()
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(TASK_TYPE, TOPIC_ID, LOCK_TIME, LOCK_OWNER, 5, acquisition, true);

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
    public void shouldPollSubscription() throws Exception
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(TASK_TYPE, TOPIC_ID, LOCK_TIME, LOCK_OWNER, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        acquisition.onTask(SUBSCRIPTION_ID, 1L, task());
        acquisition.onTask(SUBSCRIPTION_ID, 2L, task());

        // when
        int workCount = subscription.poll(taskHandler);

        // then
        assertThat(workCount).isEqualTo(2);

        verify(taskHandler).handle(argThat(hasKey(1)));
        verify(taskHandler).handle(argThat(hasKey(2)));

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

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(TASK_TYPE, TOPIC_ID, LOCK_TIME, LOCK_OWNER, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        acquisition.onTask(SUBSCRIPTION_ID, 1L, task());
        acquisition.onTask(SUBSCRIPTION_ID, 2L, task());
        acquisition.onTask(SUBSCRIPTION_ID, 3L, task());
        acquisition.onTask(SUBSCRIPTION_ID, 4L, task());

        // when
        subscription.poll(taskHandler);
        subscription.fetchTasks();
        acquisition.evaluateCommands();

        // then
        verify(client, times(1)).updateSubscriptionCredits();
        verify(updateCreditsCmd).subscriptionId(subscription.getId());
        verify(updateCreditsCmd).taskType(TASK_TYPE);
        verify(updateCreditsCmd).topicId(TOPIC_ID);
        verify(updateCreditsCmd).credits(4);
        verify(updateCreditsCmd, times(1)).execute();
    }

    @Test
    public void shouldPopulateTaskProperties() throws Exception
    {
        // given
        final TaskSubscriptions subscriptions = new TaskSubscriptions();
        final TaskAcquisition acquisition = new TaskAcquisition(client, subscriptions, taskCollector);

        final TaskSubscriptionImpl subscription = new TaskSubscriptionImpl(TASK_TYPE, TOPIC_ID, LOCK_TIME, LOCK_OWNER, 5, acquisition, true);

        subscription.openAsync();
        acquisition.evaluateCommands();

        acquisition.onTask(SUBSCRIPTION_ID, 1L, task());

        // when
        subscription.poll(taskHandler);

        // then
        verify(taskHandler).handle(argThat(new ArgumentMatcher<Task>()
        {
            @Override
            public boolean matches(Object argument)
            {
                final Task task = (Task) argument;
                return task.getKey() == 1L && TASK_TYPE.equals(task.getType());
            }
        }));
    }

    protected TaskEvent task()
    {
        final TaskEvent taskEvent = new TaskEvent();

        taskEvent.setEvent(TaskEventType.LOCKED);
        taskEvent.setType(TASK_TYPE);
        taskEvent.setLockTime(123L);
        taskEvent.setLockOwner(1);
        taskEvent.setPayload(msgPackConverter.convertToMsgPack("{}"));
        taskEvent.setHeaders(new HashMap<>());

        return taskEvent;
    }

    protected static ArgumentMatcher<Task> hasKey(final long taskKey)
    {
        return new ArgumentMatcher<Task>()
        {
            @Override
            public boolean matches(Object argument)
            {
                return argument instanceof Task && ((Task) argument).getKey() == taskKey;
            }
        };
    }

}

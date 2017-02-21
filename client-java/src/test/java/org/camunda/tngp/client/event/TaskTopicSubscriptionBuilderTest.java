package org.camunda.tngp.client.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.tngp.client.event.impl.EventAcquisition;
import org.camunda.tngp.client.event.impl.TaskTopicSubscriptionBuilderImpl;
import org.camunda.tngp.client.event.impl.TopicSubscriptionImpl;
import org.camunda.tngp.client.event.impl.TopicSubscriptionLifecycle;
import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.impl.cmd.CreateTopicSubscriptionCmdImpl;
import org.camunda.tngp.client.impl.data.MsgPackMapper;
import org.camunda.tngp.client.task.SyncContext;
import org.camunda.tngp.client.task.impl.EventSubscriptions;
import org.camunda.tngp.test.util.FluentMock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskTopicSubscriptionBuilderTest
{

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected EventSubscriptions<TopicSubscriptionImpl> subscriptions;

    @Mock
    protected TngpClientImpl client;

    @Mock
    protected MsgPackMapper msgPackMapper;

    @FluentMock
    protected CreateTopicSubscriptionCmdImpl openSubscriptionCmd;

    protected EventAcquisition<TopicSubscriptionImpl> acquisition;

    protected TopicEventHandler noOpHandler = (m, e) ->
    { };

    protected TaskEventHandler noOpTaskHandler = (m, t) ->
    { };

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        subscriptions = new EventSubscriptions<>();
        when(client.createTopicSubscription()).thenReturn(openSubscriptionCmd);
        when(openSubscriptionCmd.execute()).thenReturn(123L);

        acquisition = new EventAcquisition<TopicSubscriptionImpl>(subscriptions, new TopicSubscriptionLifecycle(client))
        {
            {
                asyncContext = new SyncContext();
            }
        };
    }


    @Test
    public void shouldBuildManagedSubscription()
    {
        // given
        final TaskTopicSubscriptionBuilder builder = new TaskTopicSubscriptionBuilderImpl(14, acquisition, msgPackMapper)
                .defaultHandler(noOpHandler).taskEventHandler(noOpTaskHandler);

        // when
        final TopicSubscriptionImpl subscription = (TopicSubscriptionImpl) builder.open();

        // then
        assertThat(subscriptions.getManagedSubscriptions()).contains(subscription);
        assertThat(subscription.getTopicId()).isEqualTo(14);
        assertThat(subscription.getId()).isEqualTo(123L);
        assertThat(subscription.getHandler()).isNotNull();

        verify(client).createTopicSubscription();
        verify(openSubscriptionCmd).topicId(14);
        verify(openSubscriptionCmd).execute();
    }

    @Test
    public void shouldValidateAtLeastOneEventHandlerForManagedSubscription()
    {
        // given
        final TaskTopicSubscriptionBuilder builder = new TaskTopicSubscriptionBuilderImpl(14, acquisition, msgPackMapper);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("handlers must have at least one non-null value");

        // when
        builder.open();
    }

    @Test
    public void shouldValidateTopicIdForManagedSubscription()
    {
        // given
        final TaskTopicSubscriptionBuilder builder = new TaskTopicSubscriptionBuilderImpl(-1, acquisition, msgPackMapper)
                .defaultHandler(noOpHandler);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("topicId must be greater than or equal to 0");

        // when
        builder.open();
    }
}

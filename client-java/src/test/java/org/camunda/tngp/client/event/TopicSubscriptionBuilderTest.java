package org.camunda.tngp.client.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.tngp.client.event.impl.EventAcquisition;
import org.camunda.tngp.client.event.impl.PollableTopicSubscriptionBuilderImpl;
import org.camunda.tngp.client.event.impl.TopicClientImpl;
import org.camunda.tngp.client.event.impl.TopicSubscriptionBuilderImpl;
import org.camunda.tngp.client.event.impl.TopicSubscriptionImpl;
import org.camunda.tngp.client.impl.cmd.CreateTopicSubscriptionCmdImpl;
import org.camunda.tngp.client.task.SyncContext;
import org.camunda.tngp.client.task.impl.EventSubscriptions;
import org.camunda.tngp.test.util.FluentMock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TopicSubscriptionBuilderTest
{

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected EventSubscriptions<TopicSubscriptionImpl> subscriptions;

    @Mock
    protected TopicClientImpl client;

    @FluentMock
    protected CreateTopicSubscriptionCmdImpl openSubscriptionCmd;

    protected EventAcquisition<TopicSubscriptionImpl> acquisition;

    protected TopicEventHandler noOpHandler = (m, e) ->
    { };

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        subscriptions = new EventSubscriptions<>();
        when(client.createTopicSubscription()).thenReturn(openSubscriptionCmd);
        when(openSubscriptionCmd.execute()).thenReturn(123L);

        acquisition = new EventAcquisition<TopicSubscriptionImpl>(subscriptions)
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

        final TopicSubscriptionBuilder builder = new TopicSubscriptionBuilderImpl(client, acquisition)
                .handler(noOpHandler);

        // when
        final TopicSubscriptionImpl subscription = (TopicSubscriptionImpl) builder.open();

        // then
        assertThat(subscriptions.getManagedSubscriptions()).contains(subscription);
        assertThat(subscription.getId()).isEqualTo(123L);
        assertThat(subscription.getHandler()).isNotNull();

        verify(client).createTopicSubscription();
        verify(openSubscriptionCmd).execute();
    }

    @Test
    public void shouldBuildPollableSubscription()
    {
        // given
        final PollableTopicSubscriptionBuilder builder = new PollableTopicSubscriptionBuilderImpl(client, acquisition);

        // when
        final TopicSubscriptionImpl subscription = (TopicSubscriptionImpl) builder.open();

        // then
        assertThat(subscriptions.getPollableSubscriptions()).contains(subscription);
        assertThat(subscription.getId()).isEqualTo(123L);
        assertThat(subscription.getHandler()).isNull();

        verify(client).createTopicSubscription();
        verify(openSubscriptionCmd).execute();
    }

    @Test
    public void shouldValidateEventHandlerForManagedSubscription()
    {
        // given
        final TopicSubscriptionBuilder builder = new TopicSubscriptionBuilderImpl(client, acquisition);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("handler must not be null");

        // when
        builder.open();
    }

}

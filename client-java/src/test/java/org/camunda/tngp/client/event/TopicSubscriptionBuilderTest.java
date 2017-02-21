package org.camunda.tngp.client.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.tngp.client.event.impl.EventAcquisition;
import org.camunda.tngp.client.event.impl.PollableTopicSubscriptionBuilderImpl;
import org.camunda.tngp.client.event.impl.TopicSubscriptionBuilderImpl;
import org.camunda.tngp.client.event.impl.TopicSubscriptionImpl;
import org.camunda.tngp.client.event.impl.TopicSubscriptionLifecycle;
import org.camunda.tngp.client.impl.TngpClientImpl;
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
    protected TngpClientImpl client;

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

        final TopicSubscriptionBuilder builder = new TopicSubscriptionBuilderImpl(14, acquisition)
                .handler(noOpHandler);

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
    public void shouldBuildPollableSubscription()
    {
        // given
        final PollableTopicSubscriptionBuilder builder = new PollableTopicSubscriptionBuilderImpl(14, acquisition);

        // when
        final TopicSubscriptionImpl subscription = (TopicSubscriptionImpl) builder.open();

        // then
        assertThat(subscriptions.getPollableSubscriptions()).contains(subscription);
        assertThat(subscription.getTopicId()).isEqualTo(14);
        assertThat(subscription.getId()).isEqualTo(123L);
        assertThat(subscription.getHandler()).isNull();

        verify(client).createTopicSubscription();
        verify(openSubscriptionCmd).topicId(14);
        verify(openSubscriptionCmd).execute();
    }

    @Test
    public void shouldValidateEventHandlerForManagedSubscription()
    {
        // given
        final TopicSubscriptionBuilder builder = new TopicSubscriptionBuilderImpl(14, acquisition);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("handler must not be null");

        // when
        builder.open();
    }

    @Test
    public void shouldValidateTopicIdForManagedSubscription()
    {
        // given
        final TopicSubscriptionBuilder builder = new TopicSubscriptionBuilderImpl(-1, acquisition)
                .handler(noOpHandler);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("topicId must be greater than or equal to 0");

        // when
        builder.open();
    }

    @Test
    public void shouldValidateTopicIdForPollableSubscription()
    {
        // given
        final PollableTopicSubscriptionBuilder builder = new PollableTopicSubscriptionBuilderImpl(-1, acquisition);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("topicId must be greater than or equal to 0");

        // when
        builder.open();
    }

}

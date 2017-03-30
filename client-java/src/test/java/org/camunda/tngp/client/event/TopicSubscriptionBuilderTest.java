package org.camunda.tngp.client.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.lt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.tngp.client.event.impl.EventAcquisition;
import org.camunda.tngp.client.event.impl.PollableTopicSubscriptionBuilderImpl;
import org.camunda.tngp.client.event.impl.TopicClientImpl;
import org.camunda.tngp.client.event.impl.TopicSubscriptionBuilderImpl;
import org.camunda.tngp.client.event.impl.TopicSubscriptionImpl;
import org.camunda.tngp.client.impl.cmd.CreateTopicSubscriptionCmdImpl;
import org.camunda.tngp.client.task.SyncContext;
import org.camunda.tngp.client.task.impl.EventSubscriptionCreationResult;
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
    protected static final int PREFETCH_CAPACITY = 4;

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
        when(openSubscriptionCmd.execute()).thenReturn(new EventSubscriptionCreationResult(123L, 5));

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
        final TopicSubscriptionBuilder builder = new TopicSubscriptionBuilderImpl(client, acquisition, PREFETCH_CAPACITY)
                .handler(noOpHandler);

        // when
        final TopicSubscriptionImpl subscription = (TopicSubscriptionImpl) builder
                .name("foo")
                .open();

        // then
        assertThat(subscriptions.getManagedSubscriptions()).contains(subscription);
        assertThat(subscription.getId()).isEqualTo(123L);
        assertThat(subscription.getHandler()).isNotNull();

        verify(client).createTopicSubscription();
        verify(openSubscriptionCmd).startPosition(lt(0L)); //default is tail of topic
        verify(openSubscriptionCmd).name("foo");
        verify(openSubscriptionCmd).prefetchCapacity(PREFETCH_CAPACITY);
        verify(openSubscriptionCmd).execute();
    }

    @Test
    public void shouldBuildManagedSubscriptionAtHeadOfTopic()
    {
        // given
        final TopicSubscriptionBuilder builder = new TopicSubscriptionBuilderImpl(client, acquisition, PREFETCH_CAPACITY)
                .handler(noOpHandler)
                .startAtHeadOfTopic();

        // when
        builder
            .name("foo")
            .open();

        // then
        verify(client).createTopicSubscription();
        verify(openSubscriptionCmd).startPosition(0L);
        verify(openSubscriptionCmd).execute();
    }

    @Test
    public void shouldBuildManagedSubscriptionAtTailOfTopic()
    {
        // given
        final TopicSubscriptionBuilder builder = new TopicSubscriptionBuilderImpl(client, acquisition, PREFETCH_CAPACITY)
                .handler(noOpHandler)
                .startAtTailOfTopic();

        // when
        builder
            .name("foo")
            .open();

        // then
        verify(client).createTopicSubscription();
        verify(openSubscriptionCmd).startPosition(lt(0L));
        verify(openSubscriptionCmd).execute();
    }

    @Test
    public void shouldBuildManagedSubscriptionAtPosition()
    {
        // given
        final TopicSubscriptionBuilder builder = new TopicSubscriptionBuilderImpl(client, acquisition, PREFETCH_CAPACITY)
                .handler(noOpHandler)
                .startAtPosition(123L);

        // when
        builder
            .name("foo")
            .open();

        // then
        verify(client).createTopicSubscription();
        verify(openSubscriptionCmd).startPosition(123L);
        verify(openSubscriptionCmd).execute();
    }


    @Test
    public void shouldBuildPollableSubscription()
    {
        // given
        final PollableTopicSubscriptionBuilder builder = new PollableTopicSubscriptionBuilderImpl(client, acquisition, PREFETCH_CAPACITY);

        // when
        final TopicSubscriptionImpl subscription = (TopicSubscriptionImpl) builder
                .name("foo")
                .open();

        // then
        assertThat(subscriptions.getPollableSubscriptions()).contains(subscription);
        assertThat(subscription.getId()).isEqualTo(123L);
        assertThat(subscription.getHandler()).isNull();

        verify(client).createTopicSubscription();
        verify(openSubscriptionCmd).startPosition(lt(0L));
        verify(openSubscriptionCmd).prefetchCapacity(PREFETCH_CAPACITY);
        verify(openSubscriptionCmd).name("foo");
        verify(openSubscriptionCmd).execute();
    }


    @Test
    public void shouldBuildPollableSubscriptionAtHeadOfTopic()
    {
        // given
        final PollableTopicSubscriptionBuilder builder = new PollableTopicSubscriptionBuilderImpl(client, acquisition, PREFETCH_CAPACITY)
                .startAtHeadOfTopic();

        // when
        builder
            .name("foo")
            .open();

        // then
        verify(client).createTopicSubscription();
        verify(openSubscriptionCmd).startPosition(0L);
        verify(openSubscriptionCmd).execute();
    }

    @Test
    public void shouldBuildPollableSubscriptionAtTailOfTopic()
    {
        // given
        final PollableTopicSubscriptionBuilder builder = new PollableTopicSubscriptionBuilderImpl(client, acquisition, PREFETCH_CAPACITY)
                .startAtTailOfTopic();

        // when
        builder
            .name("foo")
            .open();

        // then
        verify(client).createTopicSubscription();
        verify(openSubscriptionCmd).startPosition(lt(0L));
        verify(openSubscriptionCmd).execute();
    }

    @Test
    public void shouldBuildPollableSubscriptionAtPosition()
    {
        // given
        final PollableTopicSubscriptionBuilder builder = new PollableTopicSubscriptionBuilderImpl(client, acquisition, PREFETCH_CAPACITY)
                .startAtPosition(123L);

        // when
        builder
            .name("foo")
            .open();

        // then
        verify(client).createTopicSubscription();
        verify(openSubscriptionCmd).startPosition(123L);
        verify(openSubscriptionCmd).execute();
    }

    @Test
    public void shouldValidateEventHandlerForManagedSubscription()
    {
        // given
        final TopicSubscriptionBuilder builder = new TopicSubscriptionBuilderImpl(client, acquisition, PREFETCH_CAPACITY);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("handler must not be null");

        // when
        builder.open();
    }

    @Test
    public void shouldValidateNameForManagedSubscription()
    {
        // given
        final TopicSubscriptionBuilder builder = new TopicSubscriptionBuilderImpl(client, acquisition, PREFETCH_CAPACITY)
                .handler(noOpHandler);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("name must not be null");

        // when
        builder.open();
    }


    @Test
    public void shouldValidateNameForPollableSubscription()
    {
        // given
        final PollableTopicSubscriptionBuilder builder = new PollableTopicSubscriptionBuilderImpl(client, acquisition, PREFETCH_CAPACITY);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("name must not be null");

        // when
        builder.open();
    }
}

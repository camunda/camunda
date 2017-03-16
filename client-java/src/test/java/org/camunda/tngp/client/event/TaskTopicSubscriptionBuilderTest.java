package org.camunda.tngp.client.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.lt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.tngp.client.event.impl.EventAcquisition;
import org.camunda.tngp.client.event.impl.TaskTopicSubscriptionBuilderImpl;
import org.camunda.tngp.client.event.impl.TopicClientImpl;
import org.camunda.tngp.client.event.impl.TopicSubscriptionImpl;
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

    protected static final int PREFETCH_CAPACITY = 4;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected EventSubscriptions<TopicSubscriptionImpl> subscriptions;

    @Mock
    protected TopicClientImpl client;

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
        final TaskTopicSubscriptionBuilder builder = new TaskTopicSubscriptionBuilderImpl(
                client, acquisition, msgPackMapper, PREFETCH_CAPACITY)
                .defaultHandler(noOpHandler).taskEventHandler(noOpTaskHandler);

        // when
        final TopicSubscriptionImpl subscription = (TopicSubscriptionImpl) builder
                .name("foo")
                .open();

        // then
        assertThat(subscriptions.getManagedSubscriptions()).contains(subscription);
        assertThat(subscription.getId()).isEqualTo(123L);
        assertThat(subscription.getHandler()).isNotNull();

        verify(client).createTopicSubscription();
        verify(openSubscriptionCmd).name("foo");
        verify(openSubscriptionCmd).prefetchCapacity(PREFETCH_CAPACITY);
        verify(openSubscriptionCmd).execute();
    }

    @Test
    public void shouldValidateAtLeastOneEventHandlerForManagedSubscription()
    {
        // given
        final TaskTopicSubscriptionBuilder builder = new TaskTopicSubscriptionBuilderImpl(
                client,
                acquisition,
                msgPackMapper,
                PREFETCH_CAPACITY);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("handlers must have at least one non-null value");

        // when
        builder.open();
    }


    @Test
    public void shouldBuildManagedSubscriptionAtHeadOfTopic()
    {
        // given
        final TaskTopicSubscriptionBuilder builder = new TaskTopicSubscriptionBuilderImpl(
                client, acquisition, msgPackMapper, PREFETCH_CAPACITY)
                .defaultHandler(noOpHandler)
                .taskEventHandler(noOpTaskHandler)
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
        final TaskTopicSubscriptionBuilder builder = new TaskTopicSubscriptionBuilderImpl(
                client, acquisition, msgPackMapper, PREFETCH_CAPACITY)
                .defaultHandler(noOpHandler)
                .taskEventHandler(noOpTaskHandler)
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
        final TaskTopicSubscriptionBuilder builder = new TaskTopicSubscriptionBuilderImpl(
                client, acquisition, msgPackMapper, PREFETCH_CAPACITY)
                .defaultHandler(noOpHandler)
                .taskEventHandler(noOpTaskHandler)
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


}

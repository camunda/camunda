package org.camunda.tngp.client.event;

/**
 * <p>Builder used to subscribed to all events of a task topic.
 *  Builds a <code>managed</code> subscription, i.e. where supplied event handlers are invoked whenever events are received.
 *
 * <p>In contrast to {@link TopicSubscriptionBuilder}, this
 * builder allows registering specific handlers for task events with a higher level API.
 *
 * <p>By default, a subscription starts at the current tail of the topic (see {@link #startAtTailOfTopic()}).
 */
public interface TaskTopicSubscriptionBuilder
{

    /**
     * Registers a handler that handles all types of events that don't have a
     * specific handler registered for.
     *
     * @param handler the handler to register
     * @return this builder
     */
    TaskTopicSubscriptionBuilder defaultHandler(TopicEventHandler handler);

    /**
     * Registers a handler for all events of type {@link TopicEventType#TASK}
     *
     * @param handler the handler to register
     * @return this builder
     */
    TaskTopicSubscriptionBuilder taskEventHandler(TaskEventHandler handler);

    /**
     * Defines the position at which to start receiving events from.
     * A <code>position</code> greater than the current tail position
     * of the topic is equivalent to starting at the tail position. In this case,
     * events with a lower position than the supplied position may be received.
     *
     * @param position the position in the topic at which to start receiving events from
     * @return this builder
     */
    TaskTopicSubscriptionBuilder startAtPosition(long position);

    /**
     * Same as invoking {@link #startAtPosition(long)} with the topic's current tail position.
     * In particular, it is guaranteed that this subscription does not receive any event that
     * was receivable before this subscription is opened.
     *
     * @return this builder
     */
    TaskTopicSubscriptionBuilder startAtTailOfTopic();

    /**
     * Same as invoking {@link #startAtPosition(long)} with <code>position = 0</code>.
     *
     * @return this builder
     */
    TaskTopicSubscriptionBuilder startAtHeadOfTopic();


    /**
     * Opens a new topic subscription with the defined parameters.
     *
     * @return a new subscription
     */
    TopicSubscription open();

}

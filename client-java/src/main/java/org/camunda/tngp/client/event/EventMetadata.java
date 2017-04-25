package org.camunda.tngp.client.event;

public interface EventMetadata
{

    /**
     * @return the name of the topic this event as published on
     */
    String getTopicName();

    /**
     * @return the id of the topic partition this event was published on
     */
    int getPartitionId();

    /**
     * @return the unique position the event has in the topic. Events are ordered by position.
     */
    long getEventPosition();

    /**
     * @return the key of the event on this topic. Multiple events can have the same key if they
     *   reflect state of the same logical entity. Keys are unique for the combination of topic and partition.
     */
    long getEventKey();

    /**
     * @return the type of the event
     */
    TopicEventType getEventType();
}

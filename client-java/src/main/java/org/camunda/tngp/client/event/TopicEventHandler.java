package org.camunda.tngp.client.event;

/**
 * General handler for all kind of events.
 */
@FunctionalInterface
public interface TopicEventHandler
{

    /**
     * @param event the next event of the topic
     */
    void handle(EventMetadata metadata, TopicEvent event) throws Exception;
}

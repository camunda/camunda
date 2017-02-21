package org.camunda.tngp.client.event;

@FunctionalInterface
public interface TopicEventHandler
{

    /**
     * @param event the next event of the topic formatted as JSON
     */
    void handle(EventMetadata metadata, TopicEvent event) throws Exception;
}

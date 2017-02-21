package org.camunda.tngp.client.event;

public interface TaskEventHandler
{

    /**
     * Handles an event of type {@link TopicEventType#TASK}
     *
     * @param metadata Event's metadata
     * @param event POJO representation of the event
     * @throws Exception any processing exception
     */
    void handle(EventMetadata metadata, TaskEvent event) throws Exception;

}

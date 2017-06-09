package org.camunda.tngp.client.event;

/**
 * Handles task events.
 */
@FunctionalInterface
public interface TaskEventHandler
{

    /**
     * @param event the next event of the topic
     */
    void handle(EventMetadata metadata, TaskEvent event) throws Exception;
}

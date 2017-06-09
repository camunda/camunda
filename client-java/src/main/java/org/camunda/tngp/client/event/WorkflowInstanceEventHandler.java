package org.camunda.tngp.client.event;

/**
 * Handles workflow instance events.
 */
@FunctionalInterface
public interface WorkflowInstanceEventHandler
{

    /**
     * @param event the next event of the topic
     */
    void handle(EventMetadata metadata, WorkflowInstanceEvent event) throws Exception;
}

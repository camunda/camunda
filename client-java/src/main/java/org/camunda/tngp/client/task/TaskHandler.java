package org.camunda.tngp.client.task;

/**
 * Implementations MUST be thread-safe.
 */
@FunctionalInterface
public interface TaskHandler
{

    /**
     * <p>Handles a task. Implements the work to be done
     * whenever a task of a certain type is executed.
     */
    void handle(Task task);

}

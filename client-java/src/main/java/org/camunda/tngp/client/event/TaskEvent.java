package org.camunda.tngp.client.event;

import java.time.Instant;
import java.util.Map;

/**
 * POJO representing an eventType of type {@link TopicEventType#TASK}.
 */
public interface TaskEvent
{

    /**
     * @return the task's type
     */
    String getType();

    /**
     * @return the name of the eventType type in the task's eventType lifecycle
     */
    String getEventType();

    /**
     * @return headers associated with this task
     */
    Map<String, String> getHeaders();

    /**
     * @return id of the lock owner
     */
    Integer getLockOwner();

    /**
     * @return remaining retries
     */
    Integer getRetries();

    /**
     * @return the time until when the task is locked
     *   and can be exclusively processed by this client.
     */
    Instant getLockExpirationTime();

    /**
     * @return JSON-formatted payload
     */
    String getPayload();
}

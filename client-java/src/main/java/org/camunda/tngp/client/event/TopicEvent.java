package org.camunda.tngp.client.event;

public interface TopicEvent
{

    /**
     * @return event encoded as JSON
     */
    String getJson();
}

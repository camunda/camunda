package io.zeebe.client.event;

public interface TopicEvent
{

    /**
     * @return event encoded as JSON
     */
    String getJson();
}

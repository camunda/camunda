package io.zeebe.client.topic.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.zeebe.client.event.TopicEventType;
import io.zeebe.client.event.impl.EventImpl;

// TODO: rename the current TopicEvent to something that reflects its purpose (=> that it is "raw")
public class NewTopicEventImpl extends EventImpl
{
    protected final String name;
    protected final int partitions;

    @JsonCreator
    public NewTopicEventImpl(
            @JsonProperty("state") String state,
            @JsonProperty("name") String name,
            @JsonProperty("partitions") int partitions)
    {
        super(TopicEventType.TOPIC, state);
        this.name = name;
        this.partitions = partitions;
    }

    public String getName()
    {
        return name;
    }

    public int getPartitions()
    {
        return partitions;
    }

}

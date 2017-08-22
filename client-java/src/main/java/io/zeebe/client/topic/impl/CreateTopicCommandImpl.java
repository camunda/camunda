package io.zeebe.client.topic.impl;

import io.zeebe.client.event.Event;
import io.zeebe.client.event.impl.EventImpl;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.impl.cmd.CommandImpl;
import io.zeebe.util.EnsureUtil;

public class CreateTopicCommandImpl extends CommandImpl<Event>
{

    protected final NewTopicEventImpl event;

    public CreateTopicCommandImpl(RequestManager client, String name, int partitions)
    {
        super(client);
        EnsureUtil.ensureNotNull("name", name);

        this.event = new NewTopicEventImpl(TopicEventType.CREATE.name(), name, partitions);
        this.event.setTopicName(client.getSystemTopic());
        this.event.setPartitionId(client.getSystemPartition());
    }


    @Override
    public EventImpl getEvent()
    {
        return event;
    }

    @Override
    public String getExpectedStatus()
    {
        return TopicEventType.CREATED.name();
    }

}

package org.camunda.tngp.client.event.impl;

import org.camunda.tngp.client.event.TopicEventType;
import org.camunda.tngp.protocol.clientapi.EventType;

public class EventTypeMapping
{
    protected static final TopicEventType[] MAPPING;

    static
    {
        MAPPING = new TopicEventType[EventType.values().length];
        MAPPING[EventType.TASK_EVENT.value()] = TopicEventType.TASK;
        MAPPING[EventType.WORKFLOW_EVENT.value()] = TopicEventType.WORKFLOW_INSTANCE;
        MAPPING[EventType.INCIDENT_EVENT.value()] = TopicEventType.INCIDENT;
        MAPPING[EventType.RAFT_EVENT.value()] = TopicEventType.RAFT;
    }

    public static TopicEventType mapEventType(EventType protocolType)
    {
        if (protocolType.value() < MAPPING.length)
        {
            return MAPPING[protocolType.value()];
        }
        else if (protocolType != org.camunda.tngp.protocol.clientapi.EventType.NULL_VAL)
        {
            return TopicEventType.UNKNOWN;
        }
        else
        {
            return null;
        }
    }
}

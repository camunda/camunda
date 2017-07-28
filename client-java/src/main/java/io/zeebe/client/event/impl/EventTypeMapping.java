/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.event.impl;

import io.zeebe.client.event.TopicEventType;
import io.zeebe.protocol.clientapi.EventType;

public class EventTypeMapping
{
    protected static final TopicEventType[] MAPPING;
    protected static final EventType[] REVERSE_MAPPING;

    static
    {
        MAPPING = new TopicEventType[EventType.values().length];
        MAPPING[EventType.TASK_EVENT.ordinal()] = TopicEventType.TASK;
        MAPPING[EventType.WORKFLOW_EVENT.ordinal()] = TopicEventType.WORKFLOW;
        MAPPING[EventType.WORKFLOW_INSTANCE_EVENT.ordinal()] = TopicEventType.WORKFLOW_INSTANCE;
        MAPPING[EventType.INCIDENT_EVENT.ordinal()] = TopicEventType.INCIDENT;
        MAPPING[EventType.RAFT_EVENT.ordinal()] = TopicEventType.RAFT;
        MAPPING[EventType.SUBSCRIBER_EVENT.ordinal()] = TopicEventType.SUBSCRIBER;
        MAPPING[EventType.SUBSCRIPTION_EVENT.ordinal()] = TopicEventType.SUBSCRIPTION;
        MAPPING[EventType.DEPLOYMENT_EVENT.ordinal()] = TopicEventType.DEPLOYMENT;

        REVERSE_MAPPING = new EventType[MAPPING.length];

        for (EventType type : EventType.values())
        {
            final TopicEventType mappedType = MAPPING[type.ordinal()];
            if (mappedType != null)
            {
                final int targetIndex = mappedType.ordinal();
                REVERSE_MAPPING[targetIndex] = type;
            }
        }
    }

    public static TopicEventType mapEventType(EventType protocolType)
    {
        if (protocolType.value() < MAPPING.length)
        {
            return MAPPING[protocolType.ordinal()];
        }
        else if (protocolType != io.zeebe.protocol.clientapi.EventType.NULL_VAL)
        {
            return TopicEventType.UNKNOWN;
        }
        else
        {
            return null;
        }
    }

    public static EventType mapEventType(TopicEventType apiType)
    {
        if (apiType.ordinal() < MAPPING.length)
        {
            return REVERSE_MAPPING[apiType.ordinal()];
        }
        else
        {
            return null;
        }
    }
}

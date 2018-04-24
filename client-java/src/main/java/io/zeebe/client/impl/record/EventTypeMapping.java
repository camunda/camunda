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
package io.zeebe.client.impl.record;

import io.zeebe.client.api.record.RecordMetadata;
import io.zeebe.protocol.clientapi.ValueType;

public class EventTypeMapping
{
    protected static final RecordMetadata.ValueType[] MAPPING;
    protected static final ValueType[] REVERSE_MAPPING;

    static
    {
        MAPPING = new RecordMetadata.ValueType[RecordMetadata.ValueType.values().length];
        MAPPING[ValueType.JOB.ordinal()] = RecordMetadata.ValueType.JOB;
        MAPPING[ValueType.WORKFLOW_INSTANCE.ordinal()] = RecordMetadata.ValueType.WORKFLOW_INSTANCE;
        MAPPING[ValueType.INCIDENT.ordinal()] = RecordMetadata.ValueType.INCIDENT;
        MAPPING[ValueType.RAFT.ordinal()] = RecordMetadata.ValueType.RAFT;
        MAPPING[ValueType.SUBSCRIBER.ordinal()] = RecordMetadata.ValueType.SUBSCRIBER;
        MAPPING[ValueType.SUBSCRIPTION.ordinal()] = RecordMetadata.ValueType.SUBSCRIPTION;
        MAPPING[ValueType.DEPLOYMENT.ordinal()] = RecordMetadata.ValueType.DEPLOYMENT;
        MAPPING[ValueType.TOPIC.ordinal()] = RecordMetadata.ValueType.TOPIC;

        REVERSE_MAPPING = new ValueType[MAPPING.length];

        for (ValueType type : ValueType.values())
        {
            final RecordMetadata.ValueType mappedType = MAPPING[type.ordinal()];
            if (mappedType != null)
            {
                final int targetIndex = mappedType.ordinal();
                REVERSE_MAPPING[targetIndex] = type;
            }
        }
    }

    public static RecordMetadata.ValueType mapEventType(ValueType protocolType)
    {
        if (protocolType.value() < MAPPING.length)
        {
            return MAPPING[protocolType.ordinal()];
        }
        else if (protocolType != ValueType.NULL_VAL)
        {
            return RecordMetadata.ValueType.UNKNOWN;
        }
        else
        {
            return null;
        }
    }

    public static ValueType mapEventType(RecordMetadata.ValueType apiType)
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

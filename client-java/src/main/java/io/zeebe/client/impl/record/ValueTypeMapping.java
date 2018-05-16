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

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map.Entry;

import io.zeebe.protocol.clientapi.ValueType;

public class ValueTypeMapping
{
    protected static final EnumMap<ValueType, io.zeebe.client.api.record.ValueType> MAPPING;
    protected static final EnumMap<io.zeebe.client.api.record.ValueType, ValueType> REVERSE_MAPPING;

    static
    {
        MAPPING = new EnumMap<>(ValueType.class);
        REVERSE_MAPPING = new EnumMap<>(io.zeebe.client.api.record.ValueType.class);

        MAPPING.put(ValueType.JOB, io.zeebe.client.api.record.ValueType.JOB);
        MAPPING.put(ValueType.WORKFLOW_INSTANCE, io.zeebe.client.api.record.ValueType.WORKFLOW_INSTANCE);
        MAPPING.put(ValueType.INCIDENT, io.zeebe.client.api.record.ValueType.INCIDENT);
        MAPPING.put(ValueType.RAFT, io.zeebe.client.api.record.ValueType.RAFT);
        MAPPING.put(ValueType.SUBSCRIBER, io.zeebe.client.api.record.ValueType.SUBSCRIBER);
        MAPPING.put(ValueType.SUBSCRIPTION, io.zeebe.client.api.record.ValueType.SUBSCRIPTION);
        MAPPING.put(ValueType.DEPLOYMENT, io.zeebe.client.api.record.ValueType.DEPLOYMENT);
        MAPPING.put(ValueType.TOPIC, io.zeebe.client.api.record.ValueType.TOPIC);
        MAPPING.put(ValueType.NULL_VAL, io.zeebe.client.api.record.ValueType.UNKNOWN);


        final Iterator<Entry<ValueType, io.zeebe.client.api.record.ValueType>> it = MAPPING.entrySet().iterator();
        while (it.hasNext())
        {
            final Entry<ValueType, io.zeebe.client.api.record.ValueType> entry = it.next();
            REVERSE_MAPPING.put(entry.getValue(), entry.getKey());
        }
    }

    public static io.zeebe.client.api.record.ValueType mapEventType(ValueType protocolType)
    {
        return MAPPING.get(protocolType);
    }

    public static ValueType mapEventType(io.zeebe.client.api.record.ValueType apiType)
    {
        return REVERSE_MAPPING.get(apiType);
    }
}

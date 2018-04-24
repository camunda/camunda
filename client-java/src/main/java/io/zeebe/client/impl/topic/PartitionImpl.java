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
package io.zeebe.client.impl.topic;

import io.zeebe.client.api.commands.Partition;

public class PartitionImpl implements Partition
{
    private int id;
    private String topic;

    @Override
    public int getId()
    {
        return id;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    @Override
    public String getTopicName()
    {
        return topic;
    }

    public void setTopic(String topic)
    {
        this.topic = topic;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("Partition [id=");
        builder.append(id);
        builder.append(", topic=");
        builder.append(topic);
        builder.append("]");
        return builder.toString();
    }
}

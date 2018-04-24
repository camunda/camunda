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

import java.util.List;

import io.zeebe.client.api.commands.Partition;
import io.zeebe.client.api.commands.Topic;

public class TopicImpl implements Topic
{
    private String name;
    private List<Partition> partitions;

    public TopicImpl(String name, List<Partition> partitions)
    {
        this.name = name;
        this.partitions = partitions;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public List<Partition> getPartitions()
    {
        return partitions;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("Topic [name=");
        builder.append(name);
        builder.append(", partitions=");
        builder.append(partitions);
        builder.append("]");
        return builder.toString();
    }


}

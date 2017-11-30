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
package io.zeebe.client.topic.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.zeebe.client.topic.Topic;
import io.zeebe.client.topic.Topics;

public class TopicsImpl implements Topics
{
    protected List<Topic> topics;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void setPartitions(List<PartitionImpl> partitions)
    {
        topics = new ArrayList<>();
        partitions.stream()
            .collect(Collectors.groupingBy(PartitionImpl::getTopicName, Collectors.toList()))
            .forEach((t, p) -> topics.add(new TopicImpl(t, (List) p)));
    }

    @Override
    public List<Topic> getTopics()
    {
        return topics;
    }
}

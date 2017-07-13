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
package io.zeebe.client;

import java.util.Properties;

import io.zeebe.client.clustering.RequestTopologyCmd;
import io.zeebe.client.impl.ZeebeClientImpl;

public interface ZeebeClient extends AutoCloseable
{
    /**
     * Provides APIs specific to topics of type <code>task</code>.
     *
     * @param topicName
     *              the name of the topic
     *
     * @param partitionId
     *            the id of the topic partition
     */
    TaskTopicClient taskTopic(String topicName, int partitionId);

    /**
     * Provides APIs specific to topics of type <code>workflow</code>.
     *
     * @param topicName
     *              the name of the topic
     *
     * @param partitionId
     *            the id of the topic partition
     */
    WorkflowTopicClient workflowTopic(String topicName, int partitionId);

    /**
     * Provides general purpose APIs for any kind of topic.
     *
     * @param topicName
     *              the name of the topic
     *
     * @param partitionId
     *            the id of the topic partition
     */
    TopicClient topic(String topicName, int partitionId);

    RequestTopologyCmd requestTopology();

    /**
     * Connects the client to the configured broker. Not thread-safe.
     */
    void connect();

    /**
     * Disconnects the client from the configured broker. Not thread-safe.
     */
    void disconnect();

    @Override
    void close();

    static ZeebeClient create(Properties properties)
    {
        return new ZeebeClientImpl(properties);
    }

}

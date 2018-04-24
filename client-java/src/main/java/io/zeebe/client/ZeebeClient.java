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

import io.zeebe.client.api.clients.TopicClient;
import io.zeebe.client.api.commands.*;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.cmd.Request;
import io.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.zeebe.client.impl.ZeebeClientImpl;

/**
 * The client to communicate with a Zeebe broker/cluster.
 * <p>
 * TODO: show how to configure and bootstrap the client
 * <p>
 * TODO: explain something about topic
 * <p>
 * TODO: explain something about command (async, rejection)
 */
public interface ZeebeClient extends AutoCloseable
{

    /**
     * A client to operate on workflows, jobs and subscriptions.
     *
     * <pre>
     * zeebeClient
     *  .topicClient("my-topic")
     *  .workflowClient()
     *  .newCreateInstanceCommand()
     *  ...
     * </pre>
     *
     * @param topicName
     *            the name of the topic to operate on
     *
     * @return a client with access to all operations on the given topic
     */
    TopicClient topicClient(String topicName);

    /**
     * A client to operate on workflows, jobs and subscriptions.
     *
     * <pre>
     * zeebeClient
     *  .topicClient()
     *  .workflowClient()
     *  .newCreateInstanceCommand()
     *  ...
     * </pre>
     *
     * @return a client with access to all operations on the configured default
     *         topic.
     */
    TopicClient topicClient();

    /**
     * An object to (de-)serialize records from/to JSON.
     *
     * <pre>
     * JobEvent job = zeebeClient
     *  .objectMapper()
     *  .fromJson(json, JobEvent.class);
     * </pre>
     *
     * @return an object that provides (de-)serialization of all records to/from JSON.
     */
    ZeebeObjectMapper objectMapper();

    /**
     * Command to create a new topic.
     *
     * <pre>
     * zeebeClient
     *  .newCreateTopicCommand()
     *  .name("my-topic")
     *  .partitions(3)
     *  .send();
     * </pre>
     *
     * @return a builder for the command
     */
    CreateTopicCommandStep1 newCreateTopicCommand();

    // TODO: Put this in the proper place
    Request<Workflow> requestWorkflowDefinitionByKey(long key);

    /**
     * Request all topics. Can be used to inspect which topics and partitions
     * have been created.
     *
     * <pre>
     * List&#60;Topic&#62; topics = zeebeClient
     *  .newTopicsRequest()
     *  .send()
     *  .join()
     *  .getTopics();
     *
     *  String topicName = topic.getName();
     * </pre>
     *
     * @return the request where you must call {@code send()}
     */
    TopicsRequestStep1 newTopicsRequest();

    /**
     * Request the current cluster topology. Can be used to inspect which
     * brokers are available at which endpoint and which broker is the leader
     * of which partition.
     *
     * <pre>
     * List&#60;BrokerInfo&#62; topics = zeebeClient
     *  .newTopologyRequest()
     *  .send()
     *  .join()
     *  .getBrokers();
     *
     *  SocketAddress address = broker.getSocketAddress();
     *
     *  List&#60;PartitionInfo&#62; partitions = broker.getPartitions();
     * </pre>
     *
     * @return the request where you must call {@code send()}
     */
    TopologyRequestStep1 newTopologyRequest();

    /**
     * @return the client's configuration
     */
    ZeebeClientConfiguration getConfiguration();

    static ZeebeClient create(Properties properties)
    {
        return newClient(properties).create();
    }

    static ZeebeClient create(ZeebeClientConfiguration configuration)
    {
        return new ZeebeClientImpl(configuration);
    }

    static ZeebeClientBuilder newClient()
    {
        return new ZeebeClientBuilderImpl();
    }

    static ZeebeClientBuilder newClient(Properties initProperties)
    {
        return ZeebeClientBuilderImpl.fromProperties(initProperties);
    }

    @Override
    void close();
}

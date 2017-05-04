package org.camunda.tngp.client;

import java.util.Properties;

import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public interface TngpClient extends AutoCloseable
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
     * Provides APIs specific to topics of type <code>incident</code>.
     *
     * @param topicName
     *              the name of the topic
     *
     * @param partitionId
     *            the id of the topic partition
     */
    IncidentTopicClient incidentTopic(String topicName, int partitionId);

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

    TransportConnectionPool getConnectionPool();

    /**
     * Connects the client to the configured broker. Not thread-safe.
     */
    void connect();

    /**
     * Disconnects the client from the configured broker. Not thread-safe.
     */
    void disconnect();

    void close();

    static TngpClient create(Properties properties)
    {
        return new TngpClientImpl(properties);
    }

}

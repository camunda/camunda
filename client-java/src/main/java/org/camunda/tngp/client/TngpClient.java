package org.camunda.tngp.client;

import java.util.Properties;

import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public interface TngpClient extends AutoCloseable
{
    /**
     * Provides APIs specific to topics of type <code>task</code>.
     *
     * @param topicId
     *            the id of the topic
     */
    TaskTopicClient taskTopic(int topicId);

    /**
     * Provides APIs specific to topics of type <code>workflow</code>.
     *
     * @param topicId
     *            the id of the topic
     */
    WorkflowTopicClient workflowTopic(int topicId);

    /**
     * Provides general purpose APIs for any kind of topic.
     *
     * @param topicId
     *            the id of the topic
     */
    TopicClient topic(int topicId);

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

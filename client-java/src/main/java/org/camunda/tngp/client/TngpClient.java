package org.camunda.tngp.client;

import java.util.Properties;

import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public interface TngpClient extends AutoCloseable
{
    AsyncTasksClient tasks();

    WorkflowsClient workflows();

    TransportConnectionPool getConnectionPool();

    void connect();

    void close();

    static TngpClient create(Properties properties)
    {
        return new TngpClientImpl(properties);
    }

}

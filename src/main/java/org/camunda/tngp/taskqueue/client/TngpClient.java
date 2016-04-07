package org.camunda.tngp.taskqueue.client;

import java.util.Properties;

import org.camunda.tngp.taskqueue.client.cmd.CompleteTaskCmd;
import org.camunda.tngp.taskqueue.client.cmd.CreateAsyncTaskCmd;
import org.camunda.tngp.taskqueue.client.cmd.PollAndLockTasksCmd;
import org.camunda.tngp.taskqueue.impl.TngpClientImpl;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public interface TngpClient extends AutoCloseable
{

    TransportConnectionPool getConnectionPool();

    CreateAsyncTaskCmd createAsyncTask();

    PollAndLockTasksCmd pollAndLockTasks();

    CompleteTaskCmd completeTask();

    void connect();

    void close();

    static TngpClient create(Properties properties)
    {
        return new TngpClientImpl(properties);
    }

}

package org.camunda.tngp.taskqueue.impl;

import static org.camunda.tngp.taskqueue.client.ClientProperties.*;

import java.net.InetSocketAddress;
import java.util.Properties;

import org.camunda.tngp.taskqueue.client.TngpClient;
import org.camunda.tngp.taskqueue.client.cmd.CompleteTaskCmd;
import org.camunda.tngp.taskqueue.client.cmd.CreateAsyncTaskCmd;
import org.camunda.tngp.taskqueue.client.cmd.PollAndLockTasksCmd;
import org.camunda.tngp.taskqueue.impl.cmd.AbstractCmdImpl;
import org.camunda.tngp.taskqueue.impl.cmd.CompleteTaskCmdImpl;
import org.camunda.tngp.taskqueue.impl.cmd.CreateAsyncTaskCmdImpl;
import org.camunda.tngp.taskqueue.impl.cmd.PollAndLockTasksCmdImpl;
import org.camunda.tngp.transport.ClientChannel;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.camunda.tngp.transport.Transports;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;


public class TngpClientImpl implements TngpClient
{
    protected final Transport transport;
    protected final TransportConnectionPool connectionPool;
    protected ClientChannel channel;
    protected InetSocketAddress contactPoint;

    public TngpClientImpl(Properties properties)
    {
        final String brokerContactPoint = properties.getProperty(BROKER_CONTACTPOINT);

        if(brokerContactPoint == null)
        {
            throw new IllegalArgumentException("Property " + BROKER_CONTACTPOINT + " must be defined.");
        }

        String hostName = brokerContactPoint;
        int port = 8080;

        final int portDelimiter = hostName.indexOf(":");
        if(portDelimiter != -1)
        {
            hostName = hostName.substring(0, portDelimiter);
            port = Integer.parseInt(brokerContactPoint.substring(portDelimiter+1, brokerContactPoint.length()));
        }

        contactPoint = new InetSocketAddress(hostName, port);
        final int maxConnections = Integer.parseInt(properties.getProperty(CLIENT_MAXCONNECTIONS, "16"));
        final int maxRequests = Integer.parseInt(properties.getProperty(CLIENT_MAXREQUESTS, "64"));
        final int sendBufferSize = Integer.parseInt(properties.getProperty(CLIENT_SENDBUFFER_SIZE, "32"));
        final ThreadingMode threadingMode = ThreadingMode.valueOf(properties.getProperty(CLIENT_THREADINGMODE, ThreadingMode.SHARED.name()));

        transport = Transports.createTransport("tngp.client")
            .sendBufferSize(1024 * 1024 * sendBufferSize)
            .maxMessageLength(1024*1024)
            .threadingMode(threadingMode)
            .build();

        connectionPool = TransportConnectionPool.newFixedCapacityPool(transport, maxConnections, maxRequests);
    }

    public void connect()
    {
        channel = transport.createClientChannel(contactPoint)
                .requestResponseChannel(connectionPool)
                .connect();
    }

    @Override
    public void close()
    {
        try
        {
            connectionPool.close();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        try
        {
            transport.close();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public TransportConnectionPool getConnectionPool()
    {
        return connectionPool;
    }

    public CreateAsyncTaskCmd createAsyncTask()
    {
        return new CreateAsyncTaskCmdImpl(this);
    }

    @Override
    public PollAndLockTasksCmd pollAndLockTasks()
    {
        return new PollAndLockTasksCmdImpl(this);
    }

    @Override
    public CompleteTaskCmd completeTask()
    {
        return new CompleteTaskCmdImpl(this);
    }

    public int determineChannelForRequest(AbstractCmdImpl<?> createAsyncTaskCmdImpl)
    {
        if(channel == null)
        {
            throw new IllegalStateException("Not connected; call connect() first.");
        }

        return channel.getId();
    }

}

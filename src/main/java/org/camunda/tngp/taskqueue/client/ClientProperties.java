package org.camunda.tngp.taskqueue.client;

public class ClientProperties
{
    /**
     * Either a hostname if the broker is running on the default port or hostname:port
     */
    public static final String BROKER_CONTACTPOINT = "tngp.client.broker.contactPoint";

    /**
     * the maximum count of concurrently open connections.
     * This should be aligned with the maximum number of threads concurrently using the client.
     */
    public static final String CLIENT_MAXCONNECTIONS = "tngp.client.maxConnections";

    /**
     * The maximum count of concurrently in flight requests.
     */
    public static final String CLIENT_MAXREQUESTS = "tngp.client.maxRequests";

    /**
     * the size of the client's send buffer in MB
     */
    public static final String CLIENT_SENDBUFFER_SIZE = "tngp.client.sendbuffer.size";

    /**
     * Possible values:
     * SHARED: a single thread is used by the client
     * DEDICATED: a dedicated thread is used for running the sender, receive and conductor agent.
     */
    public static final String CLIENT_THREADINGMODE = "tngp.client.threadingmode";
}

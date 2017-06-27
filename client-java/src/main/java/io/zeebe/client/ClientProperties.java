package io.zeebe.client;

import java.util.Properties;

import io.zeebe.client.task.TaskHandler;
import io.zeebe.client.task.TaskSubscription;

public class ClientProperties
{
    /**
     * Either a hostname if the broker is running on the default port or hostname:port
     */
    public static final String BROKER_CONTACTPOINT = "zeebe.client.broker.contactPoint";

    /**
     * The maximum count of concurrently in flight requests.
     */
    public static final String CLIENT_MAXREQUESTS = "zeebe.client.maxRequests";

    /**
     * the size of the client's send buffer in MB
     */
    public static final String CLIENT_SENDBUFFER_SIZE = "zeebe.client.sendbuffer.size";

    /**
     * Possible values:
     * SHARED: a single thread is used by the client for network communication
     * DEDICATED: a dedicated thread is used for running the sender, receive and conductor agent.
     */
    public static final String CLIENT_THREADINGMODE = "zeebe.client.threadingmode";

    /**
     * The number of threads for invocation of {@link TaskHandler}. Setting this value to 0 effectively disables
     * managed task execution via {@link TaskSubscription}s.
     */
    public static final String CLIENT_TASK_EXECUTION_THREADS = "zeebe.client.tasks.execution.threads";

    /**
     * True or false. Determines whether the task execution runtime automatically completes
     * tasks that have not been completed/unlocked/... by their {@link TaskHandler}.
     * Default value is <code>true</code>.
     */
    public static final String CLIENT_TASK_EXECUTION_AUTOCOMPLETE = "zeebe.client.tasks.execution.autocomplete";

    /**
     * Determines the maximum amount of topic events are prefetched and buffered at a time
     * before they are handled to the event handler. Default value is 32.
     */
    public static final String CLIENT_TOPIC_SUBSCRIPTION_PREFETCH_CAPACITY = "zeebe.client.event.prefetch";

    /**
     * The period of time in milliseconds for sending keep alive messages on tcp channels. Setting this appropriately
     * can avoid overhead by reopening channels after idle time.
     */
    /*
     * Optional property; Default is defined by transport
     */
    public static final String CLIENT_TCP_CHANNEL_KEEP_ALIVE_PERIOD = "zeebe.client.channel.keepalive";

    public static void setDefaults(Properties properties)
    {
        properties.putIfAbsent(BROKER_CONTACTPOINT, "127.0.0.1:51015");
        properties.putIfAbsent(CLIENT_MAXREQUESTS, "64");
        properties.putIfAbsent(CLIENT_SENDBUFFER_SIZE, "16");
        properties.putIfAbsent(CLIENT_THREADINGMODE, "SHARED");
        properties.putIfAbsent(CLIENT_TASK_EXECUTION_THREADS, "2");
        properties.putIfAbsent(CLIENT_TASK_EXECUTION_AUTOCOMPLETE, "true");
        properties.putIfAbsent(CLIENT_TOPIC_SUBSCRIPTION_PREFETCH_CAPACITY, "32");
    }
}

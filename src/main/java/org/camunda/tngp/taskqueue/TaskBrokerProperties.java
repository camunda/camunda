package org.camunda.tngp.taskqueue;

public class TaskBrokerProperties
{
    /**
     * The hostname to bind the broker's network interface to
     */
    public static final String BROKER_NETWORKING_HOSTNAME = "tngp.broker.networking.hostname";

    /**
     * Default value for {@link #BROKER_NETWORKING_HOSTNAME}
     */
    public static final String DEFAULT_BROKER_NETWORKING_HOSTNAME = "127.0.0.1";

    /**
     * The port to bind the broker's network interface to
     */
    public static final String BROKER_NETWORKING_PORT = "tngp.broker.networking.port";

    /**
     * Default value for {@link #BROKER_NETWORKING_PORT};
     */
    public static final String DEFAULT_BROKER_NETWORKING_PORT = "8800";

    /**
     * The port to bind the broker's network interface to
     */
    public static final String BROKER_TRANSPORT_SENDBUFFER_SIZE = "tngp.broker.transport.sendbuffer.size";

    /**
     * Default value for {@link #BROKER_TRANSPORT_SENDBUFFER_SIZE};
     */
    public static final String DEFAULT_BROKER_TRANSPORT_SENDBUFFER_SIZE = "16";

    /**
     * The total number of threads used by the broker.
     */
    public static final String BROKER_THREAD_COUNT = "tngp.broker.threads.count";

    /**
     * Default value for {@link #BROKER_THREAD_COUNT};
     */
    public static final String DEFAULT_BROKER_THREAD_COUNT = "1";

    /**
     * The directory under which shared memory files are kept.
     */
    public static final String BROKER_SHMWORKDIR = "tngp.broker.shmworkdir";

    /**
     * Default value for {@link #BROKER_SHMWORKDIR};
     */
    public static final String DEFAULT_BROKER_SHMWORKDIR = "/dev/shm/tngp";

    /**
     * The directory in which the log is placed
     */
    public static final String BROKER_LOG_DIR = "tngp.broker.log.dir";

    /**
     * Default value of {@link #BROKER_LOG_DIR}
     */
    public static final String DEFAULT_BROKER_LOG_DIR = "work/logs";

    /**
     * The directory in which the log is placed
     */
    public static final String BROKER_LOG_WRITEBUFFER_SIZE = "tngp.broker.log.writebuffer.size";

    /**
     * Default value for {@link #BROKER_LOG_WRITEBUFFER_SIZE}
     */
    public static final String DEFAULT_BROKER_LOG_WRITEBUFFER_SIZE = "16";

    /**
     * The size in (MB) of a log segment.
     */
    public static final String BROKER_LOG_SEGMENT_SIZE = "tngp.broker.log.segment.size";

    /**
     * The default value for {@link #BROKER_LOG_SEGMENT_SIZE}.
     */
    public static final String DEFAULT_BROKER_LOG_SEGMENT_SIZE = "512";

    /**
     * The maximum transmission unit for the broker in KB. This controls the maximum length of fragments
     * that broker can handle as a single I/O operation.
     */
    public static final String BROKER_MTU = "tngp.broker.mtu";

    /**
     * Default value for {@link #BROKER_MTU}
     */
    public static final String DEFAULT_BROKER_MTU = "1024";

    /**
     * The size of the worker's request buffer
     */
    public static final String BROKER_WORKER_REQUESTBUFFER_SIZE = "tngp.broker.worker.requestbuffer.size";

    /**
     * Default value for {@link #BROKER_WORKER_REQUESTBUFFER_SIZE};
     */
    public static final String DEFAULT_BROKER_WORKER_REQUESTBUFFER_SIZE = "32";
}

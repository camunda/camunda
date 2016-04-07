package org.camunda.tngp.taskqueue;

import static org.camunda.tngp.taskqueue.TaskBrokerProperties.*;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.DispatcherBuilder;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogBuilder;
import org.camunda.tngp.log.Logs;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.idgenerator.LastLoggedIdReader;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.camunda.tngp.taskqueue.index.TaskInstanceIndexManager;
import org.camunda.tngp.taskqueue.protocol.CompleteTaskHandler;
import org.camunda.tngp.taskqueue.protocol.CreateTaskInstanceHandler;
import org.camunda.tngp.taskqueue.protocol.LockTaskBatchHandler;
import org.camunda.tngp.taskqueue.protocol.SbeRequestDispatcher;
import org.camunda.tngp.taskqueue.worker.TaskInstanceIdReader;
import org.camunda.tngp.taskqueue.worker.TaskQueueContext;
import org.camunda.tngp.taskqueue.worker.TaskQueueWorker;
import org.camunda.tngp.transport.ServerSocketBinding;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.TransportBuilder;
import org.camunda.tngp.transport.Transports;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;
import org.camunda.tngp.transport.requestresponse.server.WorkerChannelHandler;

import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.AgentRunner;
import uk.co.real_logic.agrona.concurrent.BackoffIdleStrategy;
import uk.co.real_logic.agrona.concurrent.CompositeAgent;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import uk.co.real_logic.agrona.concurrent.ringbuffer.RingBufferDescriptor;

public class TaskBroker implements AutoCloseable
{
    protected final TaskQueueContext taskQueueContext = new TaskQueueContext();

    protected final Properties properties;
    protected String shmWorkDir;
    protected int brokerMtu;

    protected Dispatcher logWriteBuffer;
    protected OneToOneRingBuffer workerRequestBuffer;
    protected Dispatcher transportSendBuffer;

    protected Log log;
    protected Transport transport;
    protected TaskInstanceIndexManager taskInstanceIndexManager;

    protected Agent workerAgent;
    protected Agent fsAgent;
    protected List<Agent> conductorAgents = new ArrayList<>();
    protected List<Agent> networkAgents = new ArrayList<>();

    protected List<AgentRunner> agentRunners = new ArrayList<>();
    protected List<AutoCloseable> resources = new ArrayList<>();

    protected ServerSocketBinding serverSocketBinding;

    public TaskBroker(Properties properties)
    {
        this.properties = properties;
        init();
        start();
    }

    private void init()
    {
        shmWorkDir = properties.getProperty(BROKER_SHMWORKDIR, DEFAULT_BROKER_SHMWORKDIR);
        brokerMtu = Integer.parseInt(properties.getProperty(BROKER_MTU, DEFAULT_BROKER_MTU)) * 1024;

        initLogWriteBuffer();
        initLog();
        initTransportSendBuffer();
        initTransport();
        initWorkerRequestBuffer();
        initTaskInstanceIndex();
        initTaskInstanceIdGenerator();
        initProtocolHandlers();
        initDeferredResponsePool();
        initWorker();
    }

    protected void initLogWriteBuffer()
    {
        final int logWriteBufferSize = Integer.parseInt(properties.getProperty(BROKER_LOG_WRITEBUFFER_SIZE, DEFAULT_BROKER_LOG_WRITEBUFFER_SIZE)) * 1024 * 1024;

        final DispatcherBuilder bufferBuilder = Dispatchers.create("log-write-buffer");

        logWriteBuffer = bufferBuilder
                .bufferSize(logWriteBufferSize)
                .subscriberGroups(2)
                .frameMaxLength(brokerMtu)
                .agentExternallyManaged()
                .build();

        conductorAgents.add(bufferBuilder.getConductorAgent());

        taskQueueContext.setAsyncWorkBuffer(logWriteBuffer);
    }

    protected void initLog()
    {
        final String logDir = properties.getProperty(BROKER_LOG_DIR, DEFAULT_BROKER_LOG_DIR);
        final int logSegmentSize = Integer.parseInt(properties.getProperty(BROKER_LOG_SEGMENT_SIZE, DEFAULT_BROKER_LOG_SEGMENT_SIZE)) * 1024 * 1024;

        final LogBuilder logBuilder = Logs.createLog("async-task-log");

        log = logBuilder
                .logRootPath(logDir)
                .logSegmentSize(logSegmentSize)
                .writeBuffer(logWriteBuffer)
                .agentsExternallyManaged()
                .build();

        conductorAgents.add(logBuilder.getLogConductor());
        fsAgent = logBuilder.getLogAppender();

        taskQueueContext.setLog(log);
    }

    protected void initTransportSendBuffer()
    {
        final int logWriteBufferSize = Integer.parseInt(properties.getProperty(BROKER_TRANSPORT_SENDBUFFER_SIZE, DEFAULT_BROKER_TRANSPORT_SENDBUFFER_SIZE)) * 1024 * 1024;

        final DispatcherBuilder bufferBuilder = Dispatchers.create("transport-sendbuffer");

        transportSendBuffer = bufferBuilder
                .bufferSize(logWriteBufferSize)
                .frameMaxLength(brokerMtu)
                .agentExternallyManaged()
                .build();

        conductorAgents.add(bufferBuilder.getConductorAgent());

        taskQueueContext.setResponseBuffer(transportSendBuffer);
    }


    protected void initTransport()
    {
        final TransportBuilder transportBuilder = Transports.createTransport("broker-transport");

        transport = transportBuilder
                .sendBuffer(transportSendBuffer)
                .maxMessageLength(brokerMtu)
                .agentsExternallyManaged()
                .build();

        conductorAgents.add(transportBuilder.getTransportConductor());
        networkAgents.add(transportBuilder.getReceiver());
        networkAgents.add(transportBuilder.getSender());
    }

    protected void initWorkerRequestBuffer()
    {
        final int workerRequestBufferSize = Integer.parseInt(properties.getProperty(BROKER_WORKER_REQUESTBUFFER_SIZE, DEFAULT_BROKER_WORKER_REQUESTBUFFER_SIZE)) * 1024 * 1024;

        workerRequestBuffer = new OneToOneRingBuffer(new UnsafeBuffer(ByteBuffer.allocateDirect(workerRequestBufferSize + RingBufferDescriptor.TRAILER_LENGTH)));

        taskQueueContext.setRequestBuffer(workerRequestBuffer);
    }

    protected void initTaskInstanceIndex()
    {
        taskInstanceIndexManager = new TaskInstanceIndexManager(log, 1024 * 1024);
        taskInstanceIndexManager.openIndex();
        taskQueueContext.setTaskInstanceIndexManager(taskInstanceIndexManager);
    }

    protected void initTaskInstanceIdGenerator()
    {
        taskQueueContext.setTaskInstanceIdGenerator(new PrivateIdGenerator(0));
    }


    protected void initProtocolHandlers()
    {
        final SbeRequestDispatcher reqDispatcher = new SbeRequestDispatcher();

        reqDispatcher.reqisterHandler(new CreateTaskInstanceHandler(taskQueueContext));
        reqDispatcher.reqisterHandler(new LockTaskBatchHandler(taskQueueContext));
        reqDispatcher.reqisterHandler(new CompleteTaskHandler(taskQueueContext));

        taskQueueContext.setRequestHandler(reqDispatcher);
    }

    private void initDeferredResponsePool()
    {
        taskQueueContext.setResponsePool(new DeferredResponsePool(transportSendBuffer, 64));
    }

    protected void initWorker()
    {
        workerAgent = new TaskQueueWorker("task-queue-worker", taskQueueContext);
    }

    protected void start()
    {
        startAgents();

        logWriteBuffer.start();
        resources.add(logWriteBuffer);
        transportSendBuffer.start();
        resources.add(transportSendBuffer);
        log.start();
        resources.add(log);

        recoverTaskInstanceIdGenerator();
        taskInstanceIndexManager.recreateIndex();
        resources.add(taskInstanceIndexManager);

        bindTransportSocket();
    }


    protected void recoverTaskInstanceIdGenerator()
    {
        final IdGenerator idGenerator = taskQueueContext.getTaskInstanceIdGenerator();
        final LastLoggedIdReader lastLoggedIdReader = new LastLoggedIdReader();

        System.out.print("Last task instance id ... ");

        final long id = lastLoggedIdReader.recover(log, new TaskInstanceIdReader());

        System.out.println(id + ".");

        idGenerator.setLastId(id);
    }

    protected void startAgents()
    {
        int threadCount = Integer.parseInt(properties.getProperty(BROKER_THREAD_COUNT, DEFAULT_BROKER_THREAD_COUNT));

        if(threadCount < 1)
        {
            throw new RuntimeException("Threadcount must be > 1");
        }

        final int availableProcessors = Runtime.getRuntime().availableProcessors();
        int maxThreadCount = availableProcessors + 1;
        if(threadCount > maxThreadCount)
        {
            System.err.println("WARNING: configured thread count ("+threadCount+") is larger than maxThreadCount "+maxThreadCount+"). Will fallback to max thread count.");
            threadCount = maxThreadCount;
        }

        List<Agent> agentsToRun = new ArrayList<>();
        agentsToRun.add(workerAgent);
        agentsToRun.add(fsAgent);
        agentsToRun.addAll(networkAgents);
        agentsToRun.addAll(conductorAgents);

        if(threadCount > 1)
        {
            // run the two network agents in own thread
            agentRunners.add(startAgents(networkAgents));
            agentsToRun.removeAll(networkAgents);
            threadCount--;
        }

        // run remaining agents on shared thread
        agentRunners.add(startAgents(agentsToRun));

        // TODO: distribute over remaining threads
    }

    static AgentRunner startAgents(List<Agent> agents)
    {
        Agent agent;
        if(agents.size() == 1)
        {
            agent = agents.get(0);
        }
        else
        {
            agent = new CompositeAgent(agents);
        }

        BackoffIdleStrategy idleStrategy = new BackoffIdleStrategy(100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MILLISECONDS.toNanos(200));
        AgentRunner agentRunner = new AgentRunner(idleStrategy, (t)-> t.printStackTrace(), null, agent);
        AgentRunner.startOnThread(agentRunner);

        return agentRunner;
    }

    private void bindTransportSocket()
    {
        final String hostname = properties.getProperty(BROKER_NETWORKING_HOSTNAME, DEFAULT_BROKER_NETWORKING_HOSTNAME);
        final int port = Integer.parseInt(properties.getProperty(BROKER_NETWORKING_PORT, DEFAULT_BROKER_NETWORKING_PORT));

        final InetSocketAddress bindAddr = new InetSocketAddress(hostname, port);

        serverSocketBinding = transport.createServerSocketBinding(bindAddr)
            .transportChannelHandler(new WorkerChannelHandler(workerRequestBuffer))
            .bind();
        resources.add(serverSocketBinding);

        System.out.println("TnGP transport socket bound to " + bindAddr);
    }

    @Override
    public void close() throws Exception
    {
        Collections.reverse(resources);
        for (AutoCloseable resource : resources)
        {
            try
            {
                resource.close();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }

        for (AgentRunner agentRunner : agentRunners)
        {
            agentRunner.close();
        }
    }

}

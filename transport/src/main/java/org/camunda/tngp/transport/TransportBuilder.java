package org.camunda.tngp.transport;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.agrona.ErrorHandler;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.CompositeAgent;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.agrona.concurrent.status.AtomicCounter;
import org.agrona.concurrent.status.CountersManager;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.impl.TransportContext;
import org.camunda.tngp.transport.impl.agent.Conductor;
import org.camunda.tngp.transport.impl.agent.Receiver;
import org.camunda.tngp.transport.impl.agent.Sender;

public class TransportBuilder
{
    /**
     * In the same order of magnitude of what apache and nginx use.
     */
    protected static final long DEFAULT_CHANNEL_KEEP_ALIVE_PERIOD = 5000;

    public enum ThreadingMode
    {
        SHARED,
        DEDICATED;
    }

    protected final String name;

    protected Dispatcher sendBuffer;
    protected Subscription senderSubscription;

    protected int sendBufferSize = 1024 * 1024 * 16;

    protected int maxMessageLength = 1024 * 16;
    protected long channelKeepAlivePeriod = DEFAULT_CHANNEL_KEEP_ALIVE_PERIOD;

    /*
     * default is sufficient for (default / 12) state transition that can be buffered at the same time;
     * with 32 * 1024 byte, that is ~2700.
     */
    protected int stateDispatchBufferSize = 32 * 1024;

    protected ThreadingMode threadingMode = ThreadingMode.SHARED;

    protected CountersManager countersManager;

    protected TransportContext transportContext;

    protected boolean agentsExternallyManaged = false;
    protected boolean sendBufferExternallyManaged = false;

    protected Conductor conductor;
    protected Agent sendBufferConductor;
    protected Receiver receiver;
    protected Sender sender;

    protected static final int CONTROL_FRAME_BUFFER_SIZE = RingBufferDescriptor.TRAILER_LENGTH + (1024 * 4);
    protected ManyToOneRingBuffer controlFrameBuffer = new ManyToOneRingBuffer(
            new UnsafeBuffer(ByteBuffer.allocateDirect(CONTROL_FRAME_BUFFER_SIZE)));

    static final ErrorHandler DEFAULT_ERROR_HANDLER = (t) ->
    {
        t.printStackTrace();
    };

    public TransportBuilder(String name)
    {
        this.name = name;
    }

    public TransportBuilder countersManager(CountersManager countersManager)
    {
        this.countersManager = countersManager;
        return this;
    }

    public TransportBuilder sendBuffer(Dispatcher dispatcher)
    {
        this.sendBuffer = dispatcher;
        this.sendBufferExternallyManaged = true;
        return this;
    }

    public TransportBuilder senderSubscription(Subscription subscription)
    {
        this.senderSubscription = subscription;
        return this;
    }

    public TransportBuilder sendBufferSize(int writeBufferSize)
    {
        this.sendBufferSize = writeBufferSize;
        return this;
    }

    public TransportBuilder stateDispatchBufferSize(int stateDispatchBufferSize)
    {
        this.stateDispatchBufferSize = stateDispatchBufferSize;
        return this;
    }

    public TransportBuilder maxMessageLength(int maxMessageLength)
    {
        this.maxMessageLength = maxMessageLength;
        return this;
    }

    public TransportBuilder channelKeepAlivePeriod(long channelKeepAlivePeriod)
    {
        this.channelKeepAlivePeriod = channelKeepAlivePeriod;
        return this;
    }

    public TransportBuilder threadingMode(ThreadingMode mode)
    {
        this.threadingMode = mode;
        return this;
    }

    public TransportBuilder agentsExternallyManaged()
    {
        this.agentsExternallyManaged = true;
        return this;
    }

    public Transport build()
    {
        initTransportContext();
        initSendBuffer();
        initReceiver();
        initSender();
        initConductor();
        startAgents();

        return new Transport(conductor, transportContext);
    }

    protected void initConductor()
    {
        conductor = new Conductor(
                sender,
                receiver,
                controlFrameBuffer,
                maxMessageLength,
                channelKeepAlivePeriod,
                stateDispatchBufferSize);
    }

    protected void initTransportContext()
    {
        transportContext = new TransportContext();
        transportContext.setMaxMessageLength(maxMessageLength);
        transportContext.setChannelKeepAlivePeriod(channelKeepAlivePeriod);
    }

    protected void initSendBuffer()
    {
        if (!sendBufferExternallyManaged)
        {
            this.sendBuffer = Dispatchers.create(name + ".write-buffer")
                .bufferSize(sendBufferSize)
                .subscriptions("sender")
                .countersManager(countersManager)
                .conductorExternallyManaged()
                .build();

            this.sendBufferConductor = sendBuffer.getConductorAgent();
            this.senderSubscription = sendBuffer.getSubscriptionByName("sender");
        }

        transportContext.setSendBuffer(sendBuffer);

        if (senderSubscription == null)
        {
            senderSubscription = sendBuffer.openSubscription("sender");
        }

        transportContext.setSenderSubscription(senderSubscription);
    }

    protected void initReceiver()
    {
        this.receiver = new Receiver(this.transportContext);
    }

    protected void initSender()
    {
        this.sender = new Sender(controlFrameBuffer, this.transportContext);
    }

    protected void startAgents()
    {
        if (!agentsExternallyManaged)
        {
            AgentRunner[] agentRunners = null;

            if (threadingMode == ThreadingMode.SHARED)
            {
                agentRunners = new AgentRunner[1];

                if (sendBufferExternallyManaged)
                {
                    agentRunners[0] = startAgents(conductor, receiver, sender);
                }
                else
                {
                    agentRunners[0] = startAgents(conductor, sendBufferConductor, receiver, sender);
                }
            }
            else if (threadingMode == ThreadingMode.DEDICATED)
            {
                agentRunners = new AgentRunner[3];

                if (sendBufferExternallyManaged)
                {
                    agentRunners[0] = startAgents(conductor);
                }
                else
                {
                    agentRunners[0] = startAgents(conductor, sendBufferConductor);
                }
                agentRunners[1] = startAgents(receiver);
                agentRunners[2] = startAgents(sender);
            }
            else
            {
                throw new RuntimeException("unsupported threading mode " + threadingMode);
            }
            transportContext.setAgentRunners(agentRunners);
        }
    }


    protected AgentRunner startAgents(Agent... agents)
    {

        Agent agentToRun = null;

        if (agents.length == 1)
        {
            agentToRun = agents[0];
        }
        else
        {
            agentToRun = new CompositeAgent(agents);
        }

        final BackoffIdleStrategy idleStrategy = new BackoffIdleStrategy(100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MILLISECONDS.toNanos(5));

        AtomicCounter errorCounter = null;
        if (countersManager != null)
        {
            errorCounter = countersManager.newCounter(String.format("net.long_running.transport.%s.errorCounter", name));
        }

        final AgentRunner runner = new AgentRunner(idleStrategy, DEFAULT_ERROR_HANDLER, errorCounter, agentToRun);

        AgentRunner.startOnThread(runner);

        return runner;

    }

    public Conductor getTransportConductor()
    {
        return conductor;
    }

    public Sender getSender()
    {
        return sender;
    }

    public Receiver getReceiver()
    {
        return receiver;
    }
}

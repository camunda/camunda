package org.camunda.tngp.transport;

import java.util.concurrent.TimeUnit;

import org.agrona.ErrorHandler;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.CompositeAgent;
import org.agrona.concurrent.status.AtomicCounter;
import org.agrona.concurrent.status.CountersManager;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.DispatcherBuilder;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.dispatcher.impl.DispatcherConductor;
import org.camunda.tngp.transport.impl.TransportContext;
import org.camunda.tngp.transport.impl.agent.Receiver;
import org.camunda.tngp.transport.impl.agent.Sender;
import org.camunda.tngp.transport.impl.agent.TransportConductor;

public class TransportBuilder
{
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

    protected ThreadingMode threadingMode = ThreadingMode.SHARED;

    protected CountersManager countersManager;

    protected TransportContext transportContext;

    protected boolean agentsExternallyManaged = false;
    protected boolean sendBufferExternallyManaged = false;

    protected TransportConductor transportConductor;
    protected DispatcherConductor sendBufferConductor;
    protected Receiver receiver;
    protected Sender sender;

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

    public TransportBuilder maxMessageLength(int maxMessageLength)
    {
        this.maxMessageLength = maxMessageLength;
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

        return new Transport(transportContext);
    }

    protected void initConductor()
    {
        transportConductor = new TransportConductor(transportContext);
    }

    protected void initTransportContext()
    {
        transportContext = new TransportContext();
        transportContext.setMaxMessageLength(maxMessageLength);
    }

    protected void initSendBuffer()
    {
        if (!sendBufferExternallyManaged)
        {
            final DispatcherBuilder dispatcherBuilder = Dispatchers.create(name + ".write-buffer");

            this.sendBuffer = dispatcherBuilder.bufferSize(sendBufferSize)
                .subscriptions("sender")
                .countersManager(countersManager)
                .conductorExternallyManaged()
                .build();

            this.sendBufferConductor = dispatcherBuilder.getConductorAgent();
        }

        transportContext.setSendBuffer(sendBuffer);

        if (senderSubscription == null)
        {
            senderSubscription = sendBuffer.getSubscriptionByName("sender");
        }

        transportContext.setSenderSubscription(senderSubscription);
    }

    protected void initReceiver()
    {
        this.receiver = new Receiver(this.transportContext);
    }

    protected void initSender()
    {
        this.sender = new Sender(this.transportContext);
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
                    agentRunners[0] = startAgents(transportConductor, receiver, sender);
                }
                else
                {
                    agentRunners[0] = startAgents(transportConductor, sendBufferConductor, receiver, sender);
                }
            }
            else if (threadingMode == ThreadingMode.DEDICATED)
            {
                agentRunners = new AgentRunner[3];

                if (sendBufferExternallyManaged)
                {
                    agentRunners[0] = startAgents(transportConductor);
                }
                else
                {
                    agentRunners[0] = startAgents(transportConductor, sendBufferConductor);
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

    public TransportConductor getTransportConductor()
    {
        return transportConductor;
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

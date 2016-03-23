package org.camunda.tngp.transport;

import java.util.concurrent.TimeUnit;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.impl.DispatcherConductor;
import org.camunda.tngp.dispatcher.impl.DispatcherContext;
import org.camunda.tngp.transport.impl.DefaultChannelReceiveHandler;
import org.camunda.tngp.transport.impl.TransportContext;
import org.camunda.tngp.transport.impl.agent.Receiver;
import org.camunda.tngp.transport.impl.agent.Sender;
import org.camunda.tngp.transport.impl.agent.TransportConductor;

import uk.co.real_logic.agrona.ErrorHandler;
import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.AgentRunner;
import uk.co.real_logic.agrona.concurrent.AtomicCounter;
import uk.co.real_logic.agrona.concurrent.BackoffIdleStrategy;
import uk.co.real_logic.agrona.concurrent.CompositeAgent;
import uk.co.real_logic.agrona.concurrent.CountersManager;

public class TransportBuilder
{
    public static enum ThreadingMode
    {
        SHARED,
        DEDICATED;
    }

    protected final String name;

    protected Dispatcher sendBuffer;

    protected Dispatcher receiveBuffer;

    protected ChannelReceiveHandler channelReceiveHandler;

    protected int sendBufferSize = 1024 * 1024 * 16;

    protected int recevieBufferSize = 1024 * 1024 * 16;

    protected int maxMessageLength = 1024 * 16;

    protected ThreadingMode threadingMode = ThreadingMode.SHARED;

    protected CountersManager countersManager;

    protected TransportContext transportContext;

    // agents

    protected Receiver receiver;
    protected Sender sender;
    protected TransportConductor transportConductor;
    protected DispatcherContext dispatcherContext;
    protected DispatcherConductor dispatcherConductor;
    protected Agent conductor;

    static ErrorHandler DEFAULT_ERROR_HANDLER = (t) -> {
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

    public TransportBuilder thradingMode(ThreadingMode mode)
    {
        this.threadingMode = mode;
        return this;
    }

    public TransportBuilder channelReceiveHandler(ChannelReceiveHandler handler)
    {
        channelReceiveHandler = handler;
        return this;
    }

    public Transport build()
    {
        boolean hasReceiveBuffer = channelReceiveHandler == null;

        initTransportContext();
        initBuffers(hasReceiveBuffer);
        initReceiver();
        initSender();
        initConductor();
        startAgents();
        startDispatchers(hasReceiveBuffer);

        return new Transport(transportContext);
    }

    protected void startDispatchers(boolean hasReceiveBuffer)
    {
        if(sendBuffer.getStatus() == Dispatcher.STATUS_NEW)
        {
            try
            {
                sendBuffer.startSync();
            }
            catch (InterruptedException e)
            {
                LangUtil.rethrowUnchecked(e);
            }
        }
        if(hasReceiveBuffer && receiveBuffer.getStatus() == Dispatcher.STATUS_NEW)
        {
            try
            {
                receiveBuffer.startSync();
            }
            catch(InterruptedException e)
            {
                LangUtil.rethrowUnchecked(e);
            }
        }
    }

    protected void initConductor()
    {
        transportConductor = new TransportConductor(transportContext);
        dispatcherConductor = new DispatcherConductor(dispatcherContext, true);
        conductor = new CompositeAgent(transportConductor, dispatcherConductor);
    }

    protected void initTransportContext()
    {
        transportContext = new TransportContext();
        transportContext.setChannelReceiveHandler(channelReceiveHandler);
        transportContext.setMaxMessageLength(maxMessageLength);
    }

    protected void initBuffers(boolean hasReceiveBuffer)
    {
        if(sendBuffer == null || receiveBuffer == null)
        {
            dispatcherContext = new DispatcherContext();

            if(sendBuffer == null)
            {

                sendBuffer = Dispatchers.create(name + ".write-buffer")
                    .bufferSize(sendBufferSize)
                    .context(dispatcherContext)
                    .countersManager(countersManager)
                    .build();
            }


            if(hasReceiveBuffer && receiveBuffer == null)
            {
                receiveBuffer = Dispatchers.create(name + ".receive-buffer")
                    .bufferSize(recevieBufferSize)
                    .context(dispatcherContext)
                    .countersManager(countersManager)
                    .build();

                transportContext.setChannelReceiveHandler(new DefaultChannelReceiveHandler(receiveBuffer));
            }

        }

        transportContext.setSendBuffer(sendBuffer);
        transportContext.setReceiveBuffer(receiveBuffer);
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
        AgentRunner[] agentRunners = null;

        if(threadingMode == ThreadingMode.SHARED)
        {
            agentRunners = new AgentRunner[1];
            agentRunners[0] = startAgents(conductor, receiver, sender);
        }
        else if(threadingMode == ThreadingMode.DEDICATED)
        {
            agentRunners = new AgentRunner[3];
            agentRunners[0] = startAgents(conductor);
            agentRunners[1] = startAgents(receiver);
            agentRunners[2] = startAgents(sender);
        }
        else
        {
            throw new RuntimeException("unsupported threading mode " + threadingMode);
        }
        transportContext.setAgentRunners(agentRunners);
    }


    protected AgentRunner startAgents(Agent... agents)
    {

        Agent agentToRun = null;

        if(agents.length == 1)
        {
            agentToRun = agents[0];
        }
        else
        {
            agentToRun = new CompositeAgent(agents);
        }

        BackoffIdleStrategy idleStrategy = new BackoffIdleStrategy(100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MILLISECONDS.toNanos(100));

        AtomicCounter errorCounter = null;
        if(countersManager != null)
        {
            errorCounter = countersManager.newCounter(String.format("net.long_running.transport.%s.errorCounter", name));
        }

        final AgentRunner runner = new AgentRunner(idleStrategy, DEFAULT_ERROR_HANDLER, errorCounter, agentToRun);

        AgentRunner.startOnThread(runner);

        return runner;

    }


}

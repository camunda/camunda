package org.camunda.tngp.transport;

import java.util.concurrent.TimeUnit;

import org.camunda.tngp.transport.impl.TransportContext;
import org.camunda.tngp.transport.impl.agent.Receiver;
import org.camunda.tngp.transport.impl.agent.Sender;
import org.camunda.tngp.transport.impl.agent.TransportConductor;

import net.long_running.dispatcher.Dispatcher;
import net.long_running.dispatcher.Dispatchers;
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

    protected int writeBufferSize = 1024 * 1024 * 16;

    protected int maxMessageLength = 1024 * 16;

    protected ThreadingMode threadingMode = ThreadingMode.SHARED;

    protected TransportContext transportContext;

    protected Receiver receiver;
    protected Sender sender;
    protected TransportConductor transportConductor;

    protected CountersManager countersManager;


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
        this.writeBufferSize = writeBufferSize;
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

    public Transport build()
    {

        initTransportContext();
        transportContext.setMaxMessageLength(maxMessageLength);
        initWriteBuffer();
        initRecevier();
        initSender();
        initConductor();
        startAgents();

        return new Transport(transportContext);
    }

    protected void initConductor()
    {
        this.transportConductor = new TransportConductor(transportContext);
    }

    protected void initTransportContext()
    {
        this.transportContext = new TransportContext();
    }

    protected void initWriteBuffer()
    {
        if(sendBuffer == null)
        {
            try
            {
                sendBuffer = Dispatchers.create(name + ".write-buffer")
                    .bufferSize(writeBufferSize)
                    .buildAndStart();
            }
            catch (InterruptedException e)
            {
                LangUtil.rethrowUnchecked(e);
            }
        }

        transportContext.setSendBuffer(sendBuffer);
    }

    protected void initRecevier()
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
            agentRunners[0] = startAgents(transportConductor, receiver, sender);
        }
        else if(threadingMode == ThreadingMode.DEDICATED)
        {
            agentRunners = new AgentRunner[3];
            agentRunners[0] = startAgents(transportConductor);
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

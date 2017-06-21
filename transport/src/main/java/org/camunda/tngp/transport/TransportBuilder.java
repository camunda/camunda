package org.camunda.tngp.transport;

import java.nio.ByteBuffer;

import org.agrona.ErrorHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.agrona.concurrent.status.CountersManager;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.impl.TransportContext;
import org.camunda.tngp.transport.impl.agent.Conductor;
import org.camunda.tngp.transport.impl.agent.Receiver;
import org.camunda.tngp.transport.impl.agent.Sender;
import org.camunda.tngp.util.EnsureUtil;
import org.camunda.tngp.util.actor.ActorScheduler;

public class TransportBuilder
{
    /**
     * In the same order of magnitude of what apache and nginx use.
     */
    protected static final long DEFAULT_CHANNEL_KEEP_ALIVE_PERIOD = 5000;

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

    protected CountersManager countersManager;

    protected TransportContext transportContext;

    protected boolean actorsExternallyManaged = false;
    protected boolean sendBufferExternallyManaged = false;

    protected ActorScheduler actorScheduler;

    protected Conductor conductor;
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

    public TransportBuilder actorScheduler(ActorScheduler actorScheduler)
    {
        this.actorScheduler = actorScheduler;
        return this;
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

    public TransportBuilder actorsExternallyManaged()
    {
        this.actorsExternallyManaged = true;
        return this;
    }

    public Transport build()
    {
        initTransportContext();
        initSendBuffer();
        initReceiver();
        initSender();
        initConductor();
        scheduleActors();

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
                .actorScheduler(actorScheduler)
                .bufferSize(sendBufferSize)
                .subscriptions("sender")
                .countersManager(countersManager)
                .build();

            this.senderSubscription = sendBuffer.getSubscriptionByName("sender");
        }

        transportContext.setSendBuffer(sendBuffer, sendBufferExternallyManaged);

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

    protected void scheduleActors()
    {
        if (!actorsExternallyManaged)
        {
            EnsureUtil.ensureNotNull("task scheduler", actorScheduler);

            transportContext.setConductor(actorScheduler.schedule(conductor));
            transportContext.setReceiver(actorScheduler.schedule(receiver));
            transportContext.setSender(actorScheduler.schedule(sender));
        }
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

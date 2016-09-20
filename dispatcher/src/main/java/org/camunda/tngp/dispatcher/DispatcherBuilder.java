package org.camunda.tngp.dispatcher;

import static org.camunda.tngp.dispatcher.impl.log.LogBufferDescriptor.PARTITION_COUNT;
import static org.camunda.tngp.dispatcher.impl.log.LogBufferDescriptor.requiredCapacity;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.agrona.BitUtil;
import org.agrona.ErrorHandler;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.AtomicCounter;
import org.agrona.concurrent.status.AtomicLongPosition;
import org.agrona.concurrent.status.CountersManager;
import org.agrona.concurrent.status.Position;
import org.agrona.concurrent.status.UnsafeBufferPosition;
import org.camunda.tngp.dispatcher.impl.DispatcherConductor;
import org.camunda.tngp.dispatcher.impl.DispatcherContext;
import org.camunda.tngp.dispatcher.impl.allocation.AllocatedBuffer;
import org.camunda.tngp.dispatcher.impl.allocation.AllocationDescriptor;
import org.camunda.tngp.dispatcher.impl.allocation.DirectBufferAllocator;
import org.camunda.tngp.dispatcher.impl.allocation.ExternallyAllocatedBuffer;
import org.camunda.tngp.dispatcher.impl.allocation.MappedFileAllocationDescriptor;
import org.camunda.tngp.dispatcher.impl.allocation.MappedFileAllocator;
import org.camunda.tngp.dispatcher.impl.log.LogBuffer;
import org.camunda.tngp.dispatcher.impl.log.LogBufferAppender;

/**
 * Builder for a {@link Dispatcher}
 *
 */
public class DispatcherBuilder
{
    private static final ErrorHandler DEFAULT_ERROR_HANDLER = (t) ->
    {
        t.printStackTrace();
    };

    protected boolean allocateInMemory = true;

    protected ByteBuffer rawBuffer;

    protected String bufferFileName;

    protected int bufferSize = 1024 * 1024 * 1024; // default buffer size is 1 Gig

    protected int frameMaxLength;

    protected CountersManager countersManager;

    protected String dispatcherName;

    protected AtomicBuffer countersBuffer;

    protected boolean agentExternallyManaged = false;

    protected DispatcherConductor conductorAgent;

    protected IdleStrategy idleStrategy;

    protected String[] subscriptionNames;

    protected int mode = Dispatcher.MODE_PUB_SUB;

    public DispatcherBuilder(final String dispatcherName)
    {
        this.dispatcherName = dispatcherName;
    }

    public DispatcherBuilder name(final String name)
    {
        this.dispatcherName = name;
        return this;
    }

    /**
     * Provide a raw buffer to place the dispatcher's logbuffer in.
     * The dispatcher will place the log buffer at the beginning of the provided buffer, disregarding position and it's limit.
     * The provided buffer must be large enough to accommodate the dispatcher
     *
     * @see DispatcherBuilder#allocateInFile(String)
     */
    public DispatcherBuilder allocateInBuffer(final ByteBuffer rawBuffer)
    {
        this.allocateInMemory = false;
        this.rawBuffer = rawBuffer;
        return this;
    }

    /**
     * Allocate the dispatcher's buffer in the provided file by mapping it into memory. The file must exist.
     * The dispatcher will place it's buffer at the beginning of the file.
     */
    public DispatcherBuilder allocateInFile(final String fileName)
    {
        this.allocateInMemory = false;
        this.bufferFileName = fileName;
        return this;
    }

    /**
     * The number of bytes the buffer is be able to contain. Represents the size of the data section.
     * Additional space will be allocated for the meta-data sections
     */
    public DispatcherBuilder bufferSize(final int size)
    {
        this.bufferSize = size;
        return this;
    }

    public DispatcherBuilder conductorExternallyManaged()
    {
        this.agentExternallyManaged = true;
        return this;
    }

    /**
     * The idle strategy of the conductor agent. Default is
     * {@link BackoffIdleStrategy}.
     */
    public DispatcherBuilder idleStrategy(IdleStrategy idleStrategy)
    {
        this.idleStrategy = idleStrategy;
        return this;
    }

    /**
     * The max length of the data section of a frame
     */
    public DispatcherBuilder frameMaxLength(final int frameMaxLength)
    {
        this.frameMaxLength = frameMaxLength;
        return this;
    }

    public DispatcherBuilder countersManager(final CountersManager countersManager)
    {
        this.countersManager = countersManager;
        return this;
    }

    public DispatcherBuilder countersBuffer(final AtomicBuffer countersBuffer)
    {
        this.countersBuffer = countersBuffer;
        return this;
    }

    /**
     * Predefined subscriptions which will be created on startup in the order as
     * they are declared.
     */
    public DispatcherBuilder subscriptions(final String... subscriptionNames)
    {
        this.subscriptionNames = subscriptionNames;
        return this;
    }

    /**
     * Publish-Subscribe-Mode (default): multiple subscriptions can read the
     * same fragment / block concurrently in any order.
     *
     * @see #modePipeline()
     */
    public DispatcherBuilder modePubSub()
    {
        this.mode = Dispatcher.MODE_PUB_SUB;
        return this;
    }

    /**
     * Pipeline-Mode: a subscription can only read a fragment / block if the
     * previous subscription completes reading. The subscriptions must be
     * created on startup using the builder method
     * {@link #subscriptions(String...)} that defines the order.
     *
     * @see #modePubSub()
     */
    public DispatcherBuilder modePipeline()
    {
        this.mode = Dispatcher.MODE_PIPELINE;
        return this;
    }

    public Dispatcher build()
    {

        final int partitionSize = BitUtil.align(bufferSize / PARTITION_COUNT, 8);
        final int requiredCapacity = requiredCapacity(partitionSize);

        // allocate the buffer

        AllocatedBuffer allocatedBuffer = null;
        if (allocateInMemory)
        {
            allocatedBuffer = new DirectBufferAllocator().allocate(new AllocationDescriptor(requiredCapacity));
        }
        else
        {
            if (rawBuffer != null)
            {
                if (rawBuffer.remaining() < requiredCapacity)
                {
                    throw new RuntimeException("Buffer size below required capacity of " + requiredCapacity);
                }
                allocatedBuffer = new ExternallyAllocatedBuffer(rawBuffer);
            }
            else
            {
                final File bufferFile = new File(bufferFileName);
                if (!bufferFile.exists())
                {
                    throw new RuntimeException("File " + bufferFileName + " does not exist");
                }

                allocatedBuffer = new MappedFileAllocator()
                    .allocate(new MappedFileAllocationDescriptor(requiredCapacity, bufferFile));
            }
        }

        // allocate the counters

        Position publisherLimit = null;
        Position publisherPosition = null;

        if (countersManager != null)
        {
            final int publisherPositionCounter = countersManager.allocate(String.format("%s.publisher.position", dispatcherName));
            publisherPosition = new UnsafeBufferPosition((UnsafeBuffer) countersBuffer, publisherPositionCounter, countersManager);
            final int publisherLimitCounter = countersManager.allocate(String.format("%s.publisher.limit", dispatcherName));
            publisherLimit = new UnsafeBufferPosition((UnsafeBuffer) countersBuffer, publisherLimitCounter, countersManager);
        }
        else
        {
            publisherLimit = new AtomicLongPosition();
            publisherPosition = new AtomicLongPosition();
        }

        // create dispatcher

        final LogBuffer logBuffer = new LogBuffer(allocatedBuffer, partitionSize);
        final LogBufferAppender logAppender = new LogBufferAppender();

        final int bufferWindowLength = partitionSize / 4;

        final DispatcherContext context = new DispatcherContext();

        final Dispatcher dispatcher = new Dispatcher(
            logBuffer,
            logAppender,
            publisherLimit,
            publisherPosition,
            bufferWindowLength,
            subscriptionNames,
            mode,
            context,
            dispatcherName);

        conductorAgent = new DispatcherConductor(dispatcherName, context, dispatcher);

        if (!agentExternallyManaged)
        {
            IdleStrategy idleStrategy = this.idleStrategy;

            if (idleStrategy == null)
            {
                idleStrategy = new BackoffIdleStrategy(100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MILLISECONDS.toNanos(100));
            }

            AtomicCounter errorCounter = null;
            if (countersManager != null)
            {
                errorCounter = countersManager.newCounter(String.format("net.long_running.dispatcher.%s.conductor.errorCounter", dispatcherName));
            }

            final AgentRunner conductorRunner = new AgentRunner(idleStrategy, DEFAULT_ERROR_HANDLER, errorCounter, conductorAgent);
            AgentRunner.startOnThread(conductorRunner);
            context.setAgentRunner(conductorRunner);
        }

        return dispatcher;
    }

    public DispatcherConductor getConductorAgent()
    {
        return conductorAgent;
    }

}

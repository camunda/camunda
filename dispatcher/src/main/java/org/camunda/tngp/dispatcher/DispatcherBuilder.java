package org.camunda.tngp.dispatcher;

import static org.camunda.tngp.dispatcher.impl.PositionUtil.position;
import static org.camunda.tngp.dispatcher.impl.log.LogBufferDescriptor.PARTITION_COUNT;
import static org.camunda.tngp.dispatcher.impl.log.LogBufferDescriptor.requiredCapacity;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Objects;

import org.agrona.BitUtil;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.AtomicLongPosition;
import org.agrona.concurrent.status.CountersManager;
import org.agrona.concurrent.status.Position;
import org.agrona.concurrent.status.UnsafeBufferPosition;
import org.camunda.tngp.dispatcher.impl.DispatcherConductor;
import org.camunda.tngp.dispatcher.impl.DispatcherContext;
import org.camunda.tngp.dispatcher.impl.allocation.*;
import org.camunda.tngp.dispatcher.impl.log.LogBuffer;
import org.camunda.tngp.dispatcher.impl.log.LogBufferAppender;
import org.camunda.tngp.util.EnsureUtil;
import org.camunda.tngp.util.actor.ActorReference;
import org.camunda.tngp.util.actor.ActorScheduler;

/**
 * Builder for a {@link Dispatcher}
 *
 */
public class DispatcherBuilder
{
    protected boolean allocateInMemory = true;

    protected ByteBuffer rawBuffer;

    protected String bufferFileName;

    protected int bufferSize = 1024 * 1024 * 1024; // default buffer size is 1 Gig

    protected int frameMaxLength;

    protected CountersManager countersManager;

    protected String dispatcherName;

    protected AtomicBuffer countersBuffer;

    protected ActorScheduler actorScheduler;

    protected boolean agentExternallyManaged = false;

    protected String[] subscriptionNames;

    protected int mode = Dispatcher.MODE_PUB_SUB;

    protected int initialPartitionId = 0;

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

    public DispatcherBuilder actorScheduler(ActorScheduler actorScheduler)
    {
        this.actorScheduler = actorScheduler;
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

    public DispatcherBuilder initialPartitionId(int initialPartitionId)
    {
        EnsureUtil.ensureGreaterThanOrEqual("initial partition id", initialPartitionId, 0);

        this.initialPartitionId = initialPartitionId;
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

        final AllocatedBuffer allocatedBuffer = initAllocatedBuffer(partitionSize);

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
            final long initialPosition = position(initialPartitionId, 0);

            publisherLimit = new AtomicLongPosition();
            publisherLimit.setOrdered(initialPosition);

            publisherPosition = new AtomicLongPosition();
            publisherPosition.setOrdered(initialPosition);
        }

        // create dispatcher

        final LogBuffer logBuffer = new LogBuffer(allocatedBuffer, partitionSize, initialPartitionId);
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

        final DispatcherConductor conductor = new DispatcherConductor(dispatcherName, context, dispatcher);
        context.setConductor(conductor);

        if (!agentExternallyManaged)
        {
            Objects.requireNonNull(actorScheduler, "Actor scheduler cannot be null.");

            final ActorReference actorReference = actorScheduler.schedule(conductor);
            context.setConductorReference(actorReference);
        }

        return dispatcher;
    }

    protected AllocatedBuffer initAllocatedBuffer(final int partitionSize)
    {
        final int requiredCapacity = requiredCapacity(partitionSize);

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
        return allocatedBuffer;
    }
}

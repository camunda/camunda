package org.camunda.tngp.dispatcher;

import static org.camunda.tngp.dispatcher.impl.log.LogBufferDescriptor.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.dispatcher.impl.DispatcherConductor;
import org.camunda.tngp.dispatcher.impl.DispatcherContext;
import org.camunda.tngp.dispatcher.impl.allocation.AllocatedBuffer;
import org.camunda.tngp.dispatcher.impl.allocation.AllocationDescriptor;
import org.camunda.tngp.dispatcher.impl.allocation.DirectBufferAllocator;
import org.camunda.tngp.dispatcher.impl.allocation.ExternallyAllocatedBuffer;
import org.camunda.tngp.dispatcher.impl.allocation.MappedFileAllocationDescriptor;
import org.camunda.tngp.dispatcher.impl.allocation.MappedFileAllocator;
import org.camunda.tngp.dispatcher.impl.log.LogBufferAppender;
import org.camunda.tngp.dispatcher.impl.log.LogBuffer;

import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.ErrorHandler;
import uk.co.real_logic.agrona.concurrent.AgentRunner;
import uk.co.real_logic.agrona.concurrent.AtomicCounter;
import uk.co.real_logic.agrona.concurrent.BackoffIdleStrategy;
import uk.co.real_logic.agrona.concurrent.CountersManager;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.agrona.concurrent.status.AtomicLongPosition;
import uk.co.real_logic.agrona.concurrent.status.Position;
import uk.co.real_logic.agrona.concurrent.status.UnsafeBufferPosition;

/**
 * Builder for a {@link Dispatcher}
 *
 */
public class DispatcherBuilder
{

    static ErrorHandler DEFAULT_ERROR_HANDLER = (t) -> {
        t.printStackTrace();
    };

    protected boolean allocateInMemory = true;

    protected ByteBuffer rawBuffer;

    protected String bufferFileName;

    protected int bufferSize = 1024 * 1024 * 1024; // default buffer size is 1 Gig

    protected int frameMaxLength;

    protected int subscriberGroupCount = 1;

    protected CountersManager countersManager;

    protected final String dispatcherName;

    private UnsafeBuffer countersBuffer;

    protected DispatcherContext context;

    public DispatcherBuilder(String dispatcherName)
    {
        this.dispatcherName = dispatcherName;
    }

    /**
     * Provide a raw buffer to place the dispatcher's logbuffer in.
     * The dispatcher will place the log buffer at the beginning of the provided buffer, disregarding position and it's limit.
     * The provided buffer must be large enough to accommodate the dispatcher
     *
     * @see DispatcherBuilder#allocateInFile(String)
     */
    public DispatcherBuilder allocateInBuffer(ByteBuffer rawBuffer)
    {
        this.allocateInMemory = false;
        this.rawBuffer = rawBuffer;
        return this;
    }

    /**
     * Allocate the dispatcher's buffer in the provided file by mapping it into memory. The file must exist.
     * The dispatcher will place it's buffer at the beginning of the file.
     */
    public DispatcherBuilder allocateInFile(String fileName)
    {
        this.allocateInMemory = false;
        this.bufferFileName = fileName;
        return this;
    }

    /**
     * The number of bytes the buffer is be able to contain. Represents the size of the data section.
     * Additional space will be allocated for the meta-data sections
     */
    public DispatcherBuilder bufferSize(int size)
    {
        this.bufferSize = size;
        return this;
    }

    /**
     * The max length of the data section of a frame
     */
    public DispatcherBuilder frameMaxLength(int frameMaxLength)
    {
        this.frameMaxLength = frameMaxLength;
        return this;
    }

    public DispatcherBuilder countersManager(CountersManager countersManager)
    {
        this.countersManager = countersManager;
        return this;
    }

    /**
     * The number of subscriber groups supported by this dispatcher
     * For each subscriber group, the dispatcher maintains a position field, indicating the current position of that subscriber group.
     * Subscribers within the same group are mutually exclusive: if multiple subscribers from the same group poll the dispatcher,
     * each message will be received by exactly one of the subscribers.
     *
     * @param groupCount
     * @return
     */
    public DispatcherBuilder subscriberGroups(int groupCount)
    {
        this.subscriberGroupCount = groupCount;
        return this;
    }

    public DispatcherBuilder countersBuffers(UnsafeBuffer labelsBuffer, UnsafeBuffer countersBuffer)
    {
        this.countersBuffer = countersBuffer;
        this.countersManager = new CountersManager(labelsBuffer, countersBuffer);
        return this;
    }

    public DispatcherBuilder context(DispatcherContext context)
    {
        this.context = context;
        return this;
    }

    public Dispatcher buildAndStart() throws InterruptedException
    {
        final Dispatcher dispatcher = build();

        dispatcher.startSync();

        return dispatcher;
    }

    public Dispatcher build()
    {

        final int partitionSize = BitUtil.align(bufferSize / PARTITION_COUNT, 8);
        final int requiredCapacity = requiredCapacity(partitionSize);

        // allocate the buffer

        AllocatedBuffer allocatedBuffer = null;
        if(allocateInMemory)
        {
            allocatedBuffer = new DirectBufferAllocator().allocate(new AllocationDescriptor(requiredCapacity));
        }
        else {
            if(rawBuffer != null)
            {
                if(rawBuffer.remaining() < requiredCapacity)
                {
                    throw new RuntimeException("Buffer size below required capacity of "+requiredCapacity);
                }
                allocatedBuffer = new ExternallyAllocatedBuffer(rawBuffer);
            }
            else {
                File bufferFile = new File(bufferFileName);
                if(!bufferFile.exists())
                {
                    throw new RuntimeException("File "+bufferFileName + " does not exist");
                }

                allocatedBuffer = new MappedFileAllocator()
                    .allocate(new MappedFileAllocationDescriptor(requiredCapacity, bufferFile));
            }
        }

        // allocate the counters

        Position[] subscriberPositions = new Position[subscriberGroupCount];
        Position publisherLimit;
        Position publisherPosition;

        if(countersManager != null)
        {
            for (int i = 0; i < subscriberPositions.length; i++)
            {
                int counterId = countersManager.allocate(String.format("net.long_running.dispatcher.%s.subsciber.%d.position", dispatcherName, i));
                subscriberPositions[i] = new UnsafeBufferPosition(countersBuffer, counterId, countersManager);
            }
            int publisherPositionCounter = countersManager.allocate(String.format("net.long_running.dispatcher.%s.publisher.position", dispatcherName));
            publisherPosition = new UnsafeBufferPosition(countersBuffer, publisherPositionCounter, countersManager);
            int publisherLimitCounter = countersManager.allocate(String.format("net.long_running.dispatcher.%s.publisher.limit", dispatcherName));
            publisherLimit = new UnsafeBufferPosition(countersBuffer, publisherLimitCounter, countersManager);
        }
        else
        {
            for (int i = 0; i < subscriberPositions.length; i++)
            {
                subscriberPositions[i] = new AtomicLongPosition();
            }
            publisherLimit = new AtomicLongPosition();
            publisherPosition = new AtomicLongPosition();
        }

        // create dispatcher

        final LogBuffer logBuffer = new LogBuffer(allocatedBuffer, partitionSize);
        final LogBufferAppender logAppender = new LogBufferAppender();

        int bufferWindowLength = partitionSize / 4;

        if(context == null)
        {
            // create local dispatcher context for this dispatcher
            context = new DispatcherContext();

            DispatcherConductor conductor = new DispatcherConductor(context, true);
            BackoffIdleStrategy idleStrategy = new BackoffIdleStrategy(100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MILLISECONDS.toNanos(100));

            AtomicCounter errorCounter = null;
            if(countersManager != null)
            {
                errorCounter = countersManager.newCounter(String.format("net.long_running.dispatcher.%s.conductor.errorCounter", dispatcherName));
            }

            AgentRunner conductorRunner = new AgentRunner(idleStrategy, DEFAULT_ERROR_HANDLER, errorCounter, conductor);
            context.setAgentRunner(conductorRunner);
            AgentRunner.startOnThread(conductorRunner);
        }

        return new Dispatcher(
            logBuffer,
            logAppender,
            publisherLimit,
            publisherPosition,
            subscriberPositions,
            bufferWindowLength,
            context);
    }

}

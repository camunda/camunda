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

    protected CountersManager countersManager;

    protected String dispatcherName;

    protected UnsafeBuffer countersBuffer;

    protected boolean agentExternallyManaged = false;

    protected DispatcherConductor conductorAgent;

    protected int mode = Dispatcher.MODE_PUB_SUB;

    public DispatcherBuilder(String dispatcherName)
    {
        this.dispatcherName = dispatcherName;
    }

    public DispatcherBuilder name(String name)
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

    public DispatcherBuilder conductorExternallyManaged()
    {
        this.agentExternallyManaged = true;
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

    public DispatcherBuilder countersBuffers(UnsafeBuffer labelsBuffer, UnsafeBuffer countersBuffer)
    {
        this.countersBuffer = countersBuffer;
        this.countersManager = new CountersManager(labelsBuffer, countersBuffer);
        return this;
    }

    public DispatcherBuilder modePubSub()
    {
        this.mode = Dispatcher.MODE_PUB_SUB;
        return this;
    }

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

        Position publisherLimit;
        Position publisherPosition;

        if(countersManager != null)
        {
            int publisherPositionCounter = countersManager.allocate(String.format("net.long_running.dispatcher.%s.publisher.position", dispatcherName));
            publisherPosition = new UnsafeBufferPosition(countersBuffer, publisherPositionCounter, countersManager);
            int publisherLimitCounter = countersManager.allocate(String.format("net.long_running.dispatcher.%s.publisher.limit", dispatcherName));
            publisherLimit = new UnsafeBufferPosition(countersBuffer, publisherLimitCounter, countersManager);
        }
        else
        {
            publisherLimit = new AtomicLongPosition();
            publisherPosition = new AtomicLongPosition();
        }

        // create dispatcher

        final LogBuffer logBuffer = new LogBuffer(allocatedBuffer, partitionSize);
        final LogBufferAppender logAppender = new LogBufferAppender();

        int bufferWindowLength = partitionSize / 4;

        final DispatcherContext context = new DispatcherContext();


        final Dispatcher dispatcher = new Dispatcher(
            logBuffer,
            logAppender,
            publisherLimit,
            publisherPosition,
            bufferWindowLength,
            mode,
            context,
            dispatcherName);

        conductorAgent = new DispatcherConductor(dispatcherName, context, dispatcher);

        if(!agentExternallyManaged)
        {
            final BackoffIdleStrategy idleStrategy = new BackoffIdleStrategy(100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MILLISECONDS.toNanos(100));

            AtomicCounter errorCounter = null;
            if(countersManager != null)
            {
                errorCounter = countersManager.newCounter(String.format("net.long_running.dispatcher.%s.conductor.errorCounter", dispatcherName));
            }

            AgentRunner conductorRunner = new AgentRunner(idleStrategy, DEFAULT_ERROR_HANDLER, errorCounter, conductorAgent);
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

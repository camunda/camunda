package org.camunda.tngp.dispatcher;

import static org.camunda.tngp.dispatcher.impl.PositionUtil.partitionId;
import static org.camunda.tngp.dispatcher.impl.PositionUtil.partitionOffset;
import static org.camunda.tngp.dispatcher.impl.PositionUtil.position;
import static org.camunda.tngp.dispatcher.impl.log.LogBufferAppender.RESULT_PADDING_AT_END_OF_PARTITION;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.status.AtomicLongPosition;
import org.agrona.concurrent.status.Position;
import org.camunda.tngp.dispatcher.impl.DispatcherContext;
import org.camunda.tngp.dispatcher.impl.log.LogBuffer;
import org.camunda.tngp.dispatcher.impl.log.LogBufferAppender;
import org.camunda.tngp.dispatcher.impl.log.LogBufferPartition;
import org.camunda.tngp.util.actor.Actor;


/**
 * Component for sending and receiving messages between different threads.
 *
 */
public class Dispatcher implements AutoCloseable
{
    public static final int MODE_PUB_SUB = 1;
    public static final int MODE_PIPELINE = 2;

    protected final DispatcherContext context;

    protected final LogBuffer logBuffer;
    protected final LogBufferAppender logAppender;

    protected final Position publisherLimit;
    protected final Position publisherPosition;
    protected Subscription[] subscriptions;

    protected final int maxFrameLength;
    protected final int partitionSize;
    protected int logWindowLength;
    protected final String name;

    protected final int mode;

    protected boolean closed = false;

    public Dispatcher(
            LogBuffer logBuffer,
            LogBufferAppender logAppender,
            Position publisherLimit,
            Position publisherPosition,
            int logWindowLength,
            String[] subscriptionNames,
            int mode,
            DispatcherContext context,
            String name)
    {
        this.logBuffer = logBuffer;
        this.logAppender = logAppender;
        this.publisherLimit = publisherLimit;
        this.publisherPosition = publisherPosition;
        this.logWindowLength = logWindowLength;
        this.mode = mode;
        this.context = context;
        this.name = name;

        this.partitionSize = logBuffer.getPartitionSize();
        this.maxFrameLength = partitionSize / 16;

        this.subscriptions = initSubscriptions(subscriptionNames);
    }

    protected Subscription[] initSubscriptions(String[] subscriptionNames)
    {
        int subscriptionSize = 0;
        if (subscriptionNames != null)
        {
            subscriptionSize = subscriptionNames.length;
        }

        final Subscription[] subscriptions = new Subscription[subscriptionSize];

        for (int i = 0; i < subscriptionSize; i++)
        {
            final Subscription subscription = newSubscription(i, subscriptionNames[i]);
            subscriptions[i] = subscription;
        }

        return subscriptions;
    }

    /**
     * Writes the given message to the buffer. This can fail if the publisher
     * limit or the buffer partition size is reached.
     *
     * @return the new publisher position if the message was written
     *         successfully. Otherwise, the return value is negative.
     */
    public long offer(DirectBuffer msg)
    {
        return offer(msg, 0, msg.capacity(), 0);
    }

    /**
     * Writes the given message to the buffer with the given stream id. This can
     * fail if the publisher limit or the buffer partition size is reached.
     *
     * @return the new publisher position if the message was written
     *         successfully. Otherwise, the return value is negative.
     */
    public long offer(DirectBuffer msg, int streamId)
    {
        return offer(msg, 0, msg.capacity(), streamId);
    }

    /**
     * Writes the given part of the message to the buffer. This can fail if the publisher
     * limit or the buffer partition size is reached.
     *
     * @return the new publisher position if the message was written
     *         successfully. Otherwise, the return value is negative.
     */
    public long offer(DirectBuffer msg, int start, int length)
    {
        return offer(msg, start, length, 0);
    }

    /**
     * Writes the given part of the message to the buffer with the given stream
     * id. This can fail if the publisher limit or the buffer partition size is
     * reached.
     *
     * @return the new publisher position if the message was written
     *         successfully. Otherwise, the return value is negative.
     */
    public long offer(DirectBuffer msg, int start, int length, int streamId)
    {
        final long limit = publisherLimit.getVolatile();

        final int activePartitionId = logBuffer.getActivePartitionIdVolatile();
        final LogBufferPartition partition = logBuffer.getPartition(activePartitionId);

        final int partitionOffset = partition.getTailCounterVolatile();
        final long position = position(activePartitionId, partitionOffset);

        long newPosition = -1;

        if (position < limit)
        {
            final int newOffset;

            if (length < maxFrameLength)
            {
                newOffset = logAppender.appendFrame(partition,
                        activePartitionId,
                        msg,
                        start,
                        length,
                        streamId);
            }
            else
            {
                final String exceptionMessage = String.format("Message length of %s is larger than max frame length of %s",
                        length, maxFrameLength);
                throw new RuntimeException(exceptionMessage);
            }

            newPosition = updatePublisherPosition(activePartitionId, newOffset);

            publisherPosition.proposeMaxOrdered(newPosition);
        }
        return newPosition;
    }

    /**
     * Claim a fragment of the buffer with the given length. Use
     * {@link ClaimedFragment#getBuffer()} to write the message and finish the
     * operation using {@link ClaimedFragment#commit()} or
     * {@link ClaimedFragment#abort()}. Note that the claim operation can fail
     * if the publisher limit or the buffer partition size is reached.
     *
     * @return the new publisher position if the fragment was claimed
     *         successfully. Otherwise, the return value is negative.
     */
    public long claim(ClaimedFragment claim, int length)
    {
        return claim(claim, length, 0);
    }

    /**
     * Claim a fragment of the buffer with the given length and stream id. Use
     * {@link ClaimedFragment#getBuffer()} to write the message and finish the
     * operation using {@link ClaimedFragment#commit()} or
     * {@link ClaimedFragment#abort()}. Note that the claim operation can fail
     * if the publisher limit or the buffer partition size is reached.
     *
     * @return the new publisher position if the fragment was claimed
     *         successfully. Otherwise, the return value is negative.
     */
    public long claim(ClaimedFragment claim, int length, int streamId)
    {
        final long limit = publisherLimit.getVolatile();

        final int activePartitionId = logBuffer.getActivePartitionIdVolatile();
        final LogBufferPartition partition = logBuffer.getPartition(activePartitionId);

        final int partitionOffset = partition.getTailCounterVolatile();
        final long position = position(activePartitionId, partitionOffset);

        long newPosition = -1;

        if (position < limit)
        {
            final int newOffset;

            if (length < maxFrameLength)
            {
                newOffset = logAppender.claim(partition,
                        activePartitionId,
                        claim,
                        length,
                        streamId);
            }
            else
            {
                throw new RuntimeException("Cannot claim more than " + maxFrameLength + " bytes.");
            }

            newPosition = updatePublisherPosition(activePartitionId, newOffset);

            publisherPosition.proposeMaxOrdered(newPosition);
        }

        return newPosition;
    }

    /**
     * Claim a batch of fragments on the buffer with the given length. Use
     * {@link #nextFragment(int, int)} to add a new fragment to the batch. Write the
     * fragment message using {@link #getBuffer()} and {@link #getFragmentOffset()}
     * to get the buffer offset of this fragment. Complete the whole batch operation
     * by calling either {@link #commit()} or {@link #abort()}.
     * Note that the claim operation can fail
     * if the publisher limit or the buffer partition size is reached.
     *
     * @return the new publisher position if the batch was claimed
     *         successfully. Otherwise, the return value is negative.
     */
    public long claim(ClaimedFragmentBatch batch, int fragmentCount, int batchLength)
    {
        final long limit = publisherLimit.getVolatile();

        final int activePartitionId = logBuffer.getActivePartitionIdVolatile();
        final LogBufferPartition partition = logBuffer.getPartition(activePartitionId);

        final int partitionOffset = partition.getTailCounterVolatile();
        final long position = position(activePartitionId, partitionOffset);

        long newPosition = -1;

        if (position < limit)
        {
            final int newOffset;

            if (batchLength < maxFrameLength)
            {
                newOffset = logAppender.claim(partition,
                        activePartitionId,
                        batch,
                        fragmentCount,
                        batchLength);
            }
            else
            {
                throw new RuntimeException("Cannot claim more than " + maxFrameLength + " bytes.");
            }

            newPosition = updatePublisherPosition(activePartitionId, newOffset);

            publisherPosition.proposeMaxOrdered(newPosition);
        }

        return newPosition;
    }

    protected long updatePublisherPosition(final int activePartitionId, int newOffset)
    {
        long newPosition = -1;

        if (newOffset > 0)
        {
            newPosition = position(activePartitionId, newOffset);
        }
        else if (newOffset == RESULT_PADDING_AT_END_OF_PARTITION)
        {
            logBuffer.onActiveParitionFilled(activePartitionId);
            newPosition = -2;
        }

        return newPosition;
    }

    /**
     * Returns the position till the given subscription can read.
     */
    public long subscriberLimit(Subscription subscription)
    {
        long limit = -1;

        if (mode == MODE_PUB_SUB)
        {
            limit = publisherPosition.get();
        }
        else
        {
            final int subscriberId = subscription.getId();
            if (subscriberId == 0)
            {
                limit = publisherPosition.get();
            }
            else
            {
                // in pipelining mode, a subscriber's limit is the position of the
                // previous subscriber
                limit = subscriptions[subscriberId - 1].getPosition();
            }
        }

        return limit;
    }

    public int updatePublisherLimit()
    {
        long lastSubscriberPosition = -1;

        if (subscriptions.length > 0)
        {
            lastSubscriberPosition = subscriptions[subscriptions.length - 1].getPosition();

            if (MODE_PUB_SUB == mode && subscriptions.length > 1)
            {
                for (int i = 0; i < subscriptions.length - 1; i++)
                {
                    lastSubscriberPosition = Math.min(lastSubscriberPosition, subscriptions[i].getPosition());
                }
            }
        }
        else
        {
            lastSubscriberPosition = publisherLimit.get() - logWindowLength;
        }

        int partitionId = partitionId(lastSubscriberPosition);
        int partitionOffset = partitionOffset(lastSubscriberPosition) + logWindowLength;
        if (partitionOffset >= logBuffer.getPartitionSize())
        {
            ++partitionId;
            partitionOffset = logWindowLength;

        }
        final long proposedPublisherLimit = position(partitionId, partitionOffset);

        if (publisherLimit.proposeMaxOrdered(proposedPublisherLimit))
        {
            return 1;
        }

        return 0;
    }

    /**
     * Creates a new subscription with the given name.
     *
     * @throws IllegalStateException
     *             <li>if the dispatcher runs in pipeline-mode,
     *             <li>if a subscription with this name already exists
     */
    public Subscription openSubscription(String subscriptionName)
    {
        return openSubscriptionAsync(subscriptionName).join();
    }

    /**
     * Creates a new subscription with the given name asynchronously. The
     * operation fails if the dispatcher runs in pipeline-mode or a subscription
     * with this name already exists.
     */
    public CompletableFuture<Subscription> openSubscriptionAsync(String subscriptionName)
    {
        return addToDispatcherCommandQueue(() -> doOpenSubscription(subscriptionName));
    }

    protected Subscription doOpenSubscription(String subscriptionName)
    {
        if (mode == MODE_PIPELINE)
        {
            throw new IllegalStateException("Cannot open subscriptions in pipelining mode");
        }

        ensureUniqueSubscriptionName(subscriptionName);

        final Subscription[] newSubscriptions = new Subscription[subscriptions.length + 1];
        System.arraycopy(subscriptions, 0, newSubscriptions, 0, subscriptions.length);

        final int subscriberId = newSubscriptions.length - 1;

        final Subscription subscription = newSubscription(subscriberId, subscriptionName);

        newSubscriptions[subscriberId] = subscription;

        this.subscriptions = newSubscriptions;

        return subscription;
    }

    protected void ensureUniqueSubscriptionName(String subscriptionName)
    {
        if (getSubscriptionByName(subscriptionName) != null)
        {
            throw new IllegalStateException("subscription with name '" + subscriptionName + "' already exists");
        }
    }

    protected Subscription newSubscription(final int subscriptionId, final String subscriptionName)
    {
        final AtomicLongPosition position = new AtomicLongPosition();
        position.setOrdered(position(logBuffer.getActivePartitionIdVolatile(), 0));
        return new Subscription(position, subscriptionId, subscriptionName, this);
    }

    /**
     * Close the given subscription.
     *
     * @throws IllegalStateException
     *             if the dispatcher runs in pipeline-mode.
     */
    public void closeSubscription(Subscription subscriptionToClose)
    {
        closeSubscriptionAsync(subscriptionToClose).join();
    }

    /**
     * Close the given subscription asynchronously. The operation fails if the
     * dispatcher runs in pipeline-mode.
     */
    public CompletableFuture<Void> closeSubscriptionAsync(Subscription subscriptionToClose)
    {
        return addToDispatcherCommandQueue(() -> doCloseSubscription(subscriptionToClose));
    }

    protected void doCloseSubscription(Subscription subscriptionToClose)
    {
        if (closed)
        {
            return; // don't need to adjust the subscriptions when closed
        }

        if (mode == MODE_PIPELINE)
        {
            throw new IllegalStateException("Cannot close subscriptions in pipelining mode");
        }

        final int len = subscriptions.length;
        int index = 0;

        for (int i = 0; i < len; i++)
        {
            if (subscriptionToClose == subscriptions[i])
            {
                index = i;
                break;
            }
        }

        Subscription[] newSubscriptions = null;

        final int numMoved = len - index - 1;

        if (numMoved == 0)
        {
            newSubscriptions = Arrays.copyOf(subscriptions, len - 1);
        }
        else
        {
            newSubscriptions = new Subscription[len - 1];
            System.arraycopy(subscriptions, 0, newSubscriptions, 0, index);
            System.arraycopy(subscriptions, index + 1, newSubscriptions, index, numMoved);
        }

        this.subscriptions = newSubscriptions;
    }

    /**
     * Returns the subscription with the given name.
     *
     * @return the subscription or <code>null</code> if not exist
     */
    public Subscription getSubscriptionByName(String subscriptionName)
    {
        Subscription subscription = null;

        for (int i = 0; i < subscriptions.length; i++)
        {
            if (subscriptions[i].getName().equals(subscriptionName))
            {
                subscription = subscriptions[i];
                break;
            }
        }
        return subscription;
    }

    @Override
    public void close()
    {
        closeAsync().join();
    }

    public CompletableFuture<Void> closeAsync()
    {
        closed = true;

        publisherLimit.close();
        publisherPosition.close();

        final CompletableFuture<?>[] subScriptionFutures = new CompletableFuture<?>[subscriptions.length];

        for (int i = 0; i < subscriptions.length; i++)
        {
            final CompletableFuture<Void> subscriptionFuture = subscriptions[i].closeAsnyc();
            subScriptionFutures[i] = subscriptionFuture;
        }

        final CompletableFuture<Void> future =
                CompletableFuture.allOf(subScriptionFutures)
                    .thenRun(() ->
                    {
                        logBuffer.close();
                        if (context.getConductorReference() != null)
                        {
                            context.getConductorReference().close();
                        }
                    });

        return future;
    }

    protected CompletableFuture<Void> addToDispatcherCommandQueue(Runnable runnable)
    {
        return addToDispatcherCommandQueue(() ->
        {
            runnable.run();
            return null;
        });
    }

    protected <T> CompletableFuture<T> addToDispatcherCommandQueue(Callable<T> callable)
    {
        final CompletableFuture<T> future = new CompletableFuture<>();

        context.getDispatcherCommandQueue().add((d) ->
        {
            try
            {
                final T result = callable.call();
                future.complete(result);
            }
            catch (Exception e)
            {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public LogBuffer getLogBuffer()
    {
        return logBuffer;
    }

    public void setPublisherLimitOrdered(int limit)
    {
        this.publisherLimit.setOrdered(limit);

    }

    public int getMaxFrameLength()
    {
        return maxFrameLength;
    }

    public long getPublisherPosition()
    {
        return publisherPosition.get();
    }

    public int getSubscriberCount()
    {
        return subscriptions.length;
    }

    public Actor getConductor()
    {
        return context.getConductor();
    }

    @Override
    public String toString()
    {
        return "Dispatcher [" + name + "]";
    }

}

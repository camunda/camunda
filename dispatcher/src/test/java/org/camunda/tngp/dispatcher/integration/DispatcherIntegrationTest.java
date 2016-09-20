package org.camunda.tngp.dispatcher.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.Subscription;
import org.junit.Test;

public class DispatcherIntegrationTest
{

    class Consumer implements FragmentHandler
    {

        int counter = 0;

        @Override
        public int onFragment(final DirectBuffer buffer, final int offset, final int length, final int streamId, boolean isMarkedFailed)
        {
            final int newCounter = buffer.getInt(offset);
            if (newCounter  - 1 != counter)
            {
                throw new RuntimeException();
            }
            counter = newCounter;
            return FragmentHandler.CONSUME_FRAGMENT_RESULT;
        }

    }

    @Test
    public void testOffer() throws Exception
    {
        // 1 million messages
        final int totalWork = 1000000;
        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocate(4534));

        final Dispatcher dispatcher = Dispatchers.create("default")
                .bufferSize(1024 * 1024 * 10) // 10 MB buffersize
                .build();

        final Consumer consumer = new Consumer();

        final Subscription subscription = dispatcher.openSubscription("test");


        final Thread consumerThread = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                while (consumer.counter < totalWork)
                {
                    subscription.poll(consumer, Integer.MAX_VALUE);
                }
            }
        });

        consumerThread.start();

        for (int i = 1; i <= totalWork; i++)
        {
            msg.putInt(0, i);
            while (dispatcher.offer(msg) <= 0)
            {
                // spin
            }
        }

        consumerThread.join();

        dispatcher.close();
    }

    @Test
    public void testClaim() throws Exception
    {
        final int totalWork = 1000000;
        final ClaimedFragment claimedFragment = new ClaimedFragment();

        final Dispatcher dispatcher = Dispatchers.create("default")
                .bufferSize(1024 * 1024 * 10) // 10 MB buffersize
                .build();

        final Consumer consumer = new Consumer();

        final Subscription subscription = dispatcher.openSubscription("test");

        final Thread consumerThread = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                while (consumer.counter < totalWork)
                {
                    subscription.poll(consumer, Integer.MAX_VALUE);
                }
            }
        });

        consumerThread.start();

        for (int i = 1, committed = 1; committed <= totalWork; i++)
        {
            while (dispatcher.claim(claimedFragment, 64) <= 0)
            {
                // spin
            }
            final MutableDirectBuffer buffer = claimedFragment.getBuffer();
            if (i % 5 != 0)
            {
                buffer.putInt(claimedFragment.getOffset(), committed);
                claimedFragment.commit();
                committed++;
            }
            else
            {
                claimedFragment.abort();
            }
        }

        consumerThread.join();

        dispatcher.close();
    }

    @Test
    public void testPeekBlock() throws Exception
    {
        final int totalWork = 1000000;
        final ClaimedFragment claimedFragment = new ClaimedFragment();
        final BlockPeek blockPeek = new BlockPeek();

        final Dispatcher dispatcher = Dispatchers.create("default")
                .bufferSize(1024 * 1024 * 10) // 10 MB buffersize
                .build();

        final Subscription subscription = dispatcher.openSubscription("test");

        final Thread consumerThread = new Thread(new Runnable()
        {
            int counter = 0;

            @Override
            public void run()
            {
                while (counter < totalWork)
                {
                    while (subscription.peekBlock(blockPeek, alignedLength(64), false) == 0)
                    {

                    }
                    final int newCounter = blockPeek.getRawBuffer().getInt(messageOffset(blockPeek.getBufferOffset()));
                    if (newCounter  - 1 != counter)
                    {
                        throw new RuntimeException();
                    }
                    counter = newCounter;
                    blockPeek.markCompleted();
                }
            }
        });

        consumerThread.start();

        for (int i = 1, committed = 1; committed <= totalWork; i++)
        {
            while (dispatcher.claim(claimedFragment, 59) <= 0)
            {
                // spin
            }
            final MutableDirectBuffer buffer = claimedFragment.getBuffer();
            if (i % 5 != 0)
            {
                buffer.putInt(claimedFragment.getOffset(), Integer.reverseBytes(committed));
                claimedFragment.commit();
                committed++;
            }
            else
            {
                claimedFragment.abort();
            }
        }

        consumerThread.join();

        dispatcher.close();
    }

    @Test
    public void testPeekFragmentInPipelineMode() throws Exception
    {
        final int totalWork = 1000000;
        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocate(4534));

        final Consumer consumer1 = new Consumer();
        final Consumer consumer2 = new Consumer();

        final Dispatcher dispatcher = Dispatchers.create("default")
                .bufferSize(1024 * 1024 * 10) // 10 MB buffersize
                .modePipeline()
                .subscriptions("s1", "s2")
                .build();

        final Subscription subscription1 = dispatcher.getSubscriptionByName("s1");
        final Subscription subscription2 = dispatcher.getSubscriptionByName("s2");

        final Thread consumerThread1 = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                while (consumer1.counter < totalWork)
                {
                    subscription1.peekAndConsume(consumer1, Integer.MAX_VALUE);
                }
            }
        });

        final Thread consumerThread2 = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                while (consumer2.counter < totalWork)
                {
                    // in pipeline mode, the second consumer should not overtake the first consumer
                    assertThat(consumer2.counter).isLessThanOrEqualTo(consumer1.counter);

                    subscription2.peekAndConsume(consumer2, Integer.MAX_VALUE);
                }
            }
        });

        consumerThread1.start();
        consumerThread2.start();

        for (int i = 1; i <= totalWork; i++)
        {
            msg.putInt(0, i);
            while (dispatcher.offer(msg, i) <= 0)
            {
                // spin
            }
        }

        consumerThread1.join();
        consumerThread2.join();

        dispatcher.close();
    }

    @Test
    public void testPeekBlockInPipelineMode() throws Exception
    {
        final int totalWork = 1000000;
        final ClaimedFragment claimedFragment = new ClaimedFragment();

        final BlockPeek blockPeek1 = new BlockPeek();
        final BlockPeek blockPeek2 = new BlockPeek();

        final Dispatcher dispatcher = Dispatchers.create("default")
                .bufferSize(1024 * 1024 * 10) // 10 MB buffersize
                .modePipeline()
                .subscriptions("s1", "s2")
                .build();

        final Subscription subscription1 = dispatcher.getSubscriptionByName("s1");
        final Subscription subscription2 = dispatcher.getSubscriptionByName("s2");

        final AtomicInteger counter1 = new AtomicInteger(0);
        final AtomicInteger counter2 = new AtomicInteger(0);

        final Thread consumerThread1 = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while (counter1.get() < totalWork)
                {
                    while (subscription1.peekBlock(blockPeek1, alignedLength(64), false) == 0)
                    {

                    }
                    counter1.incrementAndGet();
                    blockPeek1.markCompleted();
                }
            }
        });

        final Thread consumerThread2 = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while (counter2.get() < totalWork)
                {
                    // in pipeline mode, the second consumer should not overtake the first consumer
                    assertThat(counter2.get()).isLessThanOrEqualTo(counter1.get());

                    while (subscription2.peekBlock(blockPeek2, alignedLength(64), false) == 0)
                    {

                    }
                    counter2.incrementAndGet();
                    blockPeek2.markCompleted();
                }
            }
        });

        consumerThread1.start();
        consumerThread2.start();

        for (int i = 1; i <= totalWork; i++)
        {
            while (dispatcher.claim(claimedFragment, 59) <= 0)
            {
                // spin
            }
            final MutableDirectBuffer buffer = claimedFragment.getBuffer();
            buffer.putInt(claimedFragment.getOffset(), Integer.reverseBytes(i));
            claimedFragment.commit();

        }

        consumerThread1.join();
        consumerThread2.join();

        dispatcher.close();
    }

    @Test
    public void testMarkFragmentAsFailed() throws Exception
    {
        final int totalWork = 1000000;
        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocate(4534));

        final AtomicInteger counter1 = new AtomicInteger(0);
        final AtomicInteger counter2 = new AtomicInteger(0);

        final FragmentHandler failedConsumer = (buffer, offset, length, streamId, isMarkedFailed) ->
        {
            counter1.incrementAndGet();
            return FragmentHandler.FAILED_FRAGMENT_RESULT;
        };

        final FragmentHandler checkFailedConsumer = (buffer, offset, length, streamId, isMarkedFailed) ->
        {
            counter2.incrementAndGet();
            assertThat(isMarkedFailed).isTrue();
            return FragmentHandler.CONSUME_FRAGMENT_RESULT;
        };

        final Dispatcher dispatcher = Dispatchers.create("default")
                .bufferSize(1024 * 1024 * 10) // 10 MB buffersize
                .modePipeline()
                .subscriptions("s1", "s2")
                .build();

        final Subscription subscription1 = dispatcher.getSubscriptionByName("s1");
        final Subscription subscription2 = dispatcher.getSubscriptionByName("s2");

        final Thread consumerThread1 = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while (counter1.get() < totalWork)
                {
                    subscription1.peekAndConsume(failedConsumer, Integer.MAX_VALUE);
                }
            }
        });

        final Thread consumerThread2 = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                while (counter2.get() < totalWork)
                {
                    subscription2.peekAndConsume(checkFailedConsumer, Integer.MAX_VALUE);
                }
            }
        });

        consumerThread1.start();
        consumerThread2.start();

        for (int i = 1; i <= totalWork; i++)
        {
            msg.putInt(0, i);
            while (dispatcher.offer(msg) <= 0)
            {
                // spin
            }
        }

        consumerThread1.join();
        consumerThread2.join();

        dispatcher.close();
    }

}

package org.camunda.tngp.dispatcher.integration;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;

import java.nio.ByteBuffer;

import org.camunda.tngp.dispatcher.BlockHandler;
import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.impl.Subscription;
import org.junit.Test;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class DispatcherIntegrationTest
{

    class Consumer implements FragmentHandler
    {

        int counter = 0;

        @Override
        public void onFragment(DirectBuffer buffer, int offset, int length, int streamId)
        {
            int newCounter = buffer.getInt(offset);
            if(newCounter  - 1 != counter)
            {
                throw new RuntimeException();
            }
            counter = newCounter;
        }

    }

    class BlockConsumer implements BlockHandler
    {

        int counter = 0;

        @Override
        public void onBlockAvailable(ByteBuffer buffer, int blockOffset, int blockLength, int streamId, long position)
        {
            int newCounter = buffer.getInt(messageOffset(blockOffset));
            if(newCounter  - 1 != counter)
            {
                throw new RuntimeException();
            }
            counter = newCounter;
        }

    }

    @Test
    public void testOffer() throws Exception
    {
        // 1 million messages
        final int totalWork = 1000000;
        UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocate(4534));

        final Dispatcher dispatcher = Dispatchers.create("default")
                .bufferSize(1024 * 1024 * 10) // 10 MB buffersize
                .build();

        final Consumer consumer = new Consumer();

        final Subscription subscription = dispatcher.openSubscription();


        final Thread consumerThread = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                while(consumer.counter < totalWork)
                {
                    subscription.poll(consumer, Integer.MAX_VALUE);
                }
            }
        });

        consumerThread.start();

        for(int i = 1; i <= totalWork; i++)
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

        final Subscription subscription = dispatcher.openSubscription();

        final Thread consumerThread = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                while(consumer.counter < totalWork)
                {
                    subscription.poll(consumer, Integer.MAX_VALUE);
                }
            }
        });

        consumerThread.start();

        for(int i = 1, committed = 1; committed <= totalWork; i++)
        {
            while (dispatcher.claim(claimedFragment, 64) <= 0)
            {
                // spin
            }
            final MutableDirectBuffer buffer = claimedFragment.getBuffer();
            if(i % 5 != 0)
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
    public void testPollBlock() throws Exception
    {
        final int totalWork = 1000000;
        final ClaimedFragment claimedFragment = new ClaimedFragment();

        final Dispatcher dispatcher = Dispatchers.create("default")
                .bufferSize(1024 * 1024 * 10) // 10 MB buffersize
                .build();

        final BlockConsumer consumer = new BlockConsumer();

        final Subscription subscription = dispatcher.openSubscription();

        final Thread consumerThread = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                while(consumer.counter < totalWork)
                {
                    subscription.pollBlock(consumer, 1, false);
                }
            }
        });

        consumerThread.start();

        for(int i = 1, committed = 1; committed <= totalWork; i++)
        {
            while (dispatcher.claim(claimedFragment, 59) <= 0)
            {
                // spin
            }
            if(i % 5 != 0)
            {
                final MutableDirectBuffer buffer = claimedFragment.getBuffer();
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
    public void testPeekBlock() throws Exception
    {
        final int totalWork = 1000000;
        final ClaimedFragment claimedFragment = new ClaimedFragment();
        final BlockPeek blockPeek = new BlockPeek();

        final Dispatcher dispatcher = Dispatchers.create("default")
                .bufferSize(1024 * 1024 * 10) // 10 MB buffersize
                .build();

        final Subscription subscription = dispatcher.openSubscription();

        final Thread consumerThread = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                int counter = 0;
                while(counter < totalWork)
                {
                    while(subscription.peekBlock(blockPeek, alignedLength(64), false)==0)
                    {

                    }
                    int newCounter = blockPeek.getBuffer().getInt(messageOffset(blockPeek.getBufferOffset()));
                    if(newCounter  - 1 != counter)
                    {
                        throw new RuntimeException();
                    }
                    counter = newCounter;
                    blockPeek.markCompleted();
                }
            }
        });

        consumerThread.start();

        for(int i = 1, committed = 1; committed <= totalWork; i++)
        {
            while (dispatcher.claim(claimedFragment, 59) <= 0)
            {
                // spin
            }
            final MutableDirectBuffer buffer = claimedFragment.getBuffer();
            if(i % 5 != 0)
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

}

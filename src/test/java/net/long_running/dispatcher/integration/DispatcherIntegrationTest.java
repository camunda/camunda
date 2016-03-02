package net.long_running.dispatcher.integration;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Test;

import net.long_running.dispatcher.Dispatcher;
import net.long_running.dispatcher.Dispatchers;
import net.long_running.dispatcher.FragmentHandler;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

//@Ignore
public class DispatcherIntegrationTest
{

    class Consumer implements FragmentHandler
    {

        int counter = 0;

        @Override
        public void onFragment(UnsafeBuffer buffer, int offset, int length)
        {
            int newCounter = buffer.getInt(offset, ByteOrder.BIG_ENDIAN);
            if(newCounter  - 1 != counter)
            {
                throw new RuntimeException();
            }
            counter = newCounter;
        }

    }

    @Test
    public void test() throws Exception
    {
        final int totalWork = 1000000;

        ByteBuffer msg = ByteBuffer.allocate(1024*10);

        Dispatcher dispatcher = Dispatchers.create("default").buildAndStart();

        final Consumer consumer = new Consumer();


        Thread consumerThread = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                while(consumer.counter < totalWork)
                {
                    dispatcher.poll(consumer, Integer.MAX_VALUE);
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

        // message published

        consumerThread.join();

        dispatcher.close();
    }

}

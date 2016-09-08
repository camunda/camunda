package org.camunda.tngp.benchmark.dispatcher;

import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.impl.Subscription;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class DispatcherSupplier implements Runnable, FragmentHandler
{
    private static final int BURST_SIZE = Integer.getInteger("burst.size", 1);

    Dispatcher dispatcher;

    Thread consumer;

    private volatile boolean exit = false;

    protected Subscription subscription;

    protected AtomicBoolean[] burstCompleteFields = new AtomicBoolean[3];

    @Setup
    public void createDispatcher()
    {
        for (int i = 0; i < burstCompleteFields.length; i++)
        {
            burstCompleteFields[i] = new AtomicBoolean(false);
        }

        dispatcher = Dispatchers.create("default")
                .bufferSize(1024 * 1024 * 16)
                .idleStrategy(new BusySpinIdleStrategy())
                .build();

        consumer = new Thread(this);

        subscription = dispatcher.openSubscription();

        consumer.start();
    }

    @TearDown
    public void closeDispatcher()
    {
        exit = true;

        try
        {
            consumer.join();
            dispatcher.close();
        }
        catch (InterruptedException e)
        {
            LangUtil.rethrowUnchecked(e);
        }

    }

    public void run()
    {
        while (!exit)
        {
            subscription.poll(this, BURST_SIZE);
        }
    }

    public int onFragment(DirectBuffer msg, int offset, int length, int streamId, boolean foo)
    {
        final int messageId = msg.getInt(offset);

        if (messageId == BURST_SIZE - 1)
        {
            burstCompleteFields[streamId].set(true);
        }

        return CONSUME_FRAGMENT_RESULT;
    }
}

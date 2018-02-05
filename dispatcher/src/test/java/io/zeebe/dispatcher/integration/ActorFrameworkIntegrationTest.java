package io.zeebe.dispatcher.integration;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import io.zeebe.dispatcher.*;
import io.zeebe.util.sched.ZbActor;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;

public class ActorFrameworkIntegrationTest
{
    @Rule
    public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(3);

    class Consumer extends ZbActor implements FragmentHandler
    {
        final Dispatcher dispatcher;
        Subscription subscription;
        Runnable consumeHandler = this::consume;
        int counter = 0;

        Consumer(Dispatcher dispatcher)
        {
            this.dispatcher = dispatcher;
        }

        @Override
        protected void onActorStarted()
        {
            actor.await(dispatcher.openSubscriptionAsync("consumerSubscription-" + hashCode()), (s, t) ->
            {
                this.subscription = s;
                actor.consume(subscription, consumeHandler);
            });
        }

        void consume()
        {
            if (subscription.poll(this, Integer.MAX_VALUE) > 0)
            {
                actor.run(consumeHandler);
            }
        }

        @Override
        public int onFragment(final DirectBuffer buffer, final int offset, final int length, final int streamId, boolean isMarkedFailed)
        {
            final int newCounter = buffer.getInt(offset);
            if (newCounter  - 1 != counter)
            {
                throw new RuntimeException(newCounter + " " + counter);
            }
            counter = newCounter;
            return FragmentHandler.CONSUME_FRAGMENT_RESULT;
        }
    }

    class Producer extends ZbActor
    {
        final CountDownLatch latch = new CountDownLatch(1);

        final int totalWork = 10_000_000;
        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocate(4534));

        final Dispatcher dispatcher;
        int counter = 1;

        Runnable produce = this::produce;

        Producer(Dispatcher dispatcher)
        {
            this.dispatcher = dispatcher;
        }

        @Override
        protected void onActorStarted()
        {
            actor.run(produce);
        }

        void produce()
        {
            msg.putInt(0, counter);
            while (dispatcher.offer(msg) >= 0)
            {
                counter++;
                msg.putInt(0, counter);
            }

            actor.yield();
            actor.run(produce);

            if (counter >= totalWork)
            {
                latch.countDown();
            }
        }
    }

    @Test
    public void testPublish() throws InterruptedException
    {
        final Dispatcher dispatcher = Dispatchers.create("default")
                .actorScheduler(actorSchedulerRule.get())
                .bufferSize(1024 * 1024 * 10) // 10 MB buffersize
                .build();

        actorSchedulerRule.submitActor(new Consumer(dispatcher));
        final Producer producer = new Producer(dispatcher);
        actorSchedulerRule.submitActor(producer);

        producer.latch.await();

        actorSchedulerRule.get().dumpMetrics(System.out);

        dispatcher.close();
    }

}

package io.zeebe.util.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.LongConsumer;

import org.junit.Test;

public class LongRingBufferConcurrentTest
{
    @Test
    public void shouldConsumeWithParallelProducer()
    {
        // given
        final int bufferCapacity = 32;
        final long numElements = 1_000_000L;
        final LongRingBuffer ringBuffer = new LongRingBuffer(bufferCapacity);

        final Producer producer = new Producer(ringBuffer, numElements);
        final ConsumingHandler consumer = new ConsumingHandler(0L);

        // when
        new Thread(producer).start();

        while (consumer.counter < numElements)
        {
            final int numElementsConsumed = ringBuffer.consume(consumer);

            if (numElementsConsumed == 0)
            {
                Thread.yield();
            }
        }

        // then
        assertThat(consumer.counter).isEqualTo(numElements);
    }

    protected class ConsumingHandler implements LongConsumer
    {
        protected long counter;

        public ConsumingHandler(long counter)
        {
            this.counter = counter;
        }

        @Override
        public void accept(long value)
        {
            assertThat(value).isEqualTo(counter);
            counter++;
        }
    }

    protected class Producer implements Runnable
    {
        protected final LongRingBuffer ringBuffer;
        protected final long numValuesToProduce;

        public Producer(LongRingBuffer ringBuffer, long numValuesToProduce)
        {
            this.ringBuffer = ringBuffer;
            this.numValuesToProduce = numValuesToProduce;
        }

        @Override
        public void run()
        {
            for (long i = 0; i < numValuesToProduce; i++)
            {
                while (!ringBuffer.addElementToHead(i))
                {
                    Thread.yield();
                }
            }
        }

    }
}

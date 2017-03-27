package org.camunda.tngp.util.collection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class LongRingBufferTest
{

    @Test
    public void shouldAddElement()
    {
        // given
        final LongRingBuffer buffer = new LongRingBuffer(3);

        // when
        buffer.addElementToHead(1L);

        // then
        assertThat(buffer.isSaturated()).isFalse();
    }



    @Test
    public void shouldAddElementsUntilBufferFull()
    {
        // given
        final LongRingBuffer buffer = new LongRingBuffer(3);

        // when
        buffer.addElementToHead(1L);
        buffer.addElementToHead(2L);
        buffer.addElementToHead(3L);

        // then
        assertThat(buffer.isSaturated()).isTrue();
    }

    @Test
    public void shouldConsumeElements()
    {
        // given
        final LongRingBuffer buffer = new LongRingBuffer(3);

        buffer.addElementToHead(1L);
        buffer.addElementToHead(2L);
        buffer.addElementToHead(3L);

        // when
        buffer.consumeAscendingUntilInclusive(2L);

        // then
        assertThat(buffer.isSaturated()).isFalse();
    }

    @Test
    public void shouldConsumeAllElements()
    {
        // given
        final LongRingBuffer buffer = new LongRingBuffer(3);

        buffer.addElementToHead(1L);
        buffer.addElementToHead(2L);
        buffer.addElementToHead(3L);

        // when
        buffer.consumeAscendingUntilInclusive(3L);

        // then
        assertThat(buffer.isSaturated()).isFalse();
    }

    @Test
    public void shouldConsumeAllLowerElements()
    {
        // given
        final LongRingBuffer buffer = new LongRingBuffer(3);

        buffer.addElementToHead(1L);
        buffer.addElementToHead(2L);
        buffer.addElementToHead(5L);

        // when
        buffer.consumeAscendingUntilInclusive(4L);

        // then
        assertThat(buffer.size()).isEqualTo(1);
    }

    @Test
    public void shouldConsumeAllElementsIfMax()
    {
        // given
        final LongRingBuffer buffer = new LongRingBuffer(3);

        buffer.addElementToHead(1L);
        buffer.addElementToHead(2L);
        buffer.addElementToHead(3L);

        // when
        buffer.consumeAscendingUntilInclusive(4L);

        // then
        assertThat(buffer.size()).isEqualTo(0);
    }


    @Test
    public void shouldAddMoreElementsThanCapacity()
    {
        // given
        final LongRingBuffer buffer = new LongRingBuffer(3);

        buffer.addElementToHead(1L);
        buffer.addElementToHead(2L);
        buffer.addElementToHead(3L);
        buffer.consumeAscendingUntilInclusive(2L);

        // when
        buffer.addElementToHead(4L);
        buffer.addElementToHead(5L);

        // then
        assertThat(buffer.isSaturated()).isTrue();
    }
}

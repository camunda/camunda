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
        buffer.consumeUntilInclusive(2L);

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
        buffer.consumeUntilInclusive(3L);

        // then
        assertThat(buffer.isSaturated()).isFalse();
    }

    @Test
    public void shouldConsumeAllElementsIfNotExists()
    {
        // given
        final LongRingBuffer buffer = new LongRingBuffer(3);

        buffer.addElementToHead(1L);
        buffer.addElementToHead(2L);
        buffer.addElementToHead(3L);

        // when
        buffer.consumeUntilInclusive(4L);

        // then
        assertThat(buffer.isSaturated()).isFalse();
    }


    @Test
    public void shouldAddMoreElementsThanCapacity()
    {
        // given
        final LongRingBuffer buffer = new LongRingBuffer(3);

        buffer.addElementToHead(1L);
        buffer.addElementToHead(2L);
        buffer.addElementToHead(3L);
        buffer.consumeUntilInclusive(2L);

        // when
        buffer.addElementToHead(4L);
        buffer.addElementToHead(5L);

        // then
        assertThat(buffer.isSaturated()).isTrue();
    }
}

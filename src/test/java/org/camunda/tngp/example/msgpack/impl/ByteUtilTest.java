package org.camunda.tngp.example.msgpack.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class ByteUtilTest
{

    @Test
    public void testIsNumeric()
    {
        // given
        DirectBuffer buffer = new UnsafeBuffer("foo0123456789bar".getBytes(StandardCharsets.UTF_8));

        // then
        assertThat(ByteUtil.isNumeric(buffer, 0, buffer.capacity())).isFalse();
        assertThat(ByteUtil.isNumeric(buffer, 3, 10)).isTrue();
        assertThat(ByteUtil.isNumeric(buffer, 2, 10)).isFalse();
        assertThat(ByteUtil.isNumeric(buffer, 3, 11)).isFalse();
    }

    @Test
    public void testParseInteger()
    {
        // given
        DirectBuffer buffer = new UnsafeBuffer("foo56781bar".getBytes(StandardCharsets.UTF_8));

        // then
        assertThat(ByteUtil.parseInteger(buffer, 3, 5)).isEqualTo(56781);

    }
}

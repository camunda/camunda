package org.camunda.tngp.util.buffer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class BufferUtilTest
{
    protected static final byte[] BYTES1 = "foo".getBytes(StandardCharsets.UTF_8);
    protected static final byte[] BYTES2 = "bar".getBytes(StandardCharsets.UTF_8);
    protected static final byte[] BYTES3 = new byte[BYTES1.length + BYTES2.length];

    static
    {
        System.arraycopy(BYTES1, 0, BYTES3, 0, BYTES1.length);
        System.arraycopy(BYTES2, 0, BYTES3, BYTES1.length, BYTES2.length);
    }

    @Test
    public void testEquals()
    {
        assertThat(
                BufferUtil.contentsEqual(asBuffer(BYTES1), asBuffer(BYTES1)))
            .isTrue();
        assertThat(
                BufferUtil.contentsEqual(asBuffer(BYTES1), asBuffer(BYTES2)))
            .isFalse();
        assertThat(
                BufferUtil.contentsEqual(asBuffer(BYTES1), asBuffer(BYTES3)))
            .isFalse();
        assertThat(
                BufferUtil.contentsEqual(asBuffer(BYTES3), asBuffer(BYTES1)))
            .isFalse();
    }

    public DirectBuffer asBuffer(byte[] bytes)
    {
        return new UnsafeBuffer(bytes);
    }
}

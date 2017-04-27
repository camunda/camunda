package org.camunda.tngp.util.buffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.util.StringUtil.getBytes;
import static org.camunda.tngp.util.buffer.BufferUtil.cloneBuffer;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class BufferUtilTest
{
    protected static final byte[] BYTES1 = getBytes("foo");
    protected static final byte[] BYTES2 = getBytes("bar");
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

    @Test
    public void testCloneUnsafeBuffer()
    {
        // given
        final DirectBuffer src = new UnsafeBuffer(BYTES1);

        // when
        final DirectBuffer dst = cloneBuffer(src);

        // then
        assertThat(dst)
            .isNotSameAs(src)
            .isEqualTo(src)
            .hasSameClassAs(src);
    }

    @Test
    public void testCloneExpandableArrayBuffer()
    {
        // given
        final MutableDirectBuffer src = new ExpandableArrayBuffer(BYTES1.length);
        src.putBytes(0, BYTES1);

        // when
        final DirectBuffer dst = cloneBuffer(src);

        // then
        assertThat(dst)
            .isNotSameAs(src)
            .isEqualTo(src)
            .hasSameClassAs(src);
    }

    public DirectBuffer asBuffer(byte[] bytes)
    {
        return new UnsafeBuffer(bytes);
    }
}

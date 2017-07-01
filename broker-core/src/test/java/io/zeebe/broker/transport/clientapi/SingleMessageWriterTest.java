package io.zeebe.broker.transport.clientapi;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.broker.transport.clientapi.MockDispatcherFactory.dispatcherOn;
import static io.zeebe.test.util.BufferAssert.assertThatBuffer;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.transport.protocol.Protocols;
import io.zeebe.transport.protocol.TransportHeaderDescriptor;
import io.zeebe.transport.singlemessage.SingleMessageHeaderDescriptor;
import io.zeebe.util.buffer.DirectBufferWriter;
import org.junit.Test;

public class SingleMessageWriterTest
{

    public static final DirectBuffer BUFFER = wrapString("foo");
    public static final int STREAM_ID = 123;

    protected UnsafeBuffer sendBuffer = new UnsafeBuffer(new byte[1024]);

    @Test
    public void shouldWriteMessage()
    {
        // given
        final Dispatcher sendDispatcher = dispatcherOn(STREAM_ID, sendBuffer).thatDoes().claim().done();
        final SingleMessageWriter writer = new SingleMessageWriter(sendDispatcher);
        final DirectBufferWriter bufferWriter = new DirectBufferWriter();
        bufferWriter.wrap(BUFFER, 1, BUFFER.capacity() - 1);

        // when
        final boolean isWritten = writer.tryWrite(STREAM_ID, bufferWriter);

        // then
        assertThat(isWritten).isTrue();

        final int expectedMessageLength = TransportHeaderDescriptor.HEADER_LENGTH +
                SingleMessageHeaderDescriptor.HEADER_LENGTH +
                BUFFER.capacity() - 1;

        final UnsafeBuffer expectedMessage = new UnsafeBuffer(new byte[1024]);
        int offset = 0;
        expectedMessage.putInt(offset, expectedMessageLength);

        offset += DataFrameDescriptor.HEADER_LENGTH;
        expectedMessage.putShort(offset, Protocols.FULL_DUPLEX_SINGLE_MESSAGE);

        offset += TransportHeaderDescriptor.HEADER_LENGTH + SingleMessageHeaderDescriptor.HEADER_LENGTH;
        expectedMessage.putBytes(offset, BUFFER, 1, BUFFER.capacity() - 1);

        assertThatBuffer(sendBuffer).hasBytes(expectedMessage, 0, DataFrameDescriptor.alignedLength(expectedMessageLength));
    }

    @Test
    public void shouldRetryClaimFragmentIfPadding()
    {
        // given
        final Dispatcher sendDispatcher = dispatcherOn(STREAM_ID, sendBuffer).thatDoes().padding().then().claim().done();

        final SingleMessageWriter writer = new SingleMessageWriter(sendDispatcher);
        final DirectBufferWriter bufferWriter = new DirectBufferWriter();
        bufferWriter.wrap(BUFFER, 1, BUFFER.capacity() - 1);

        // when
        final boolean isWritten = writer.tryWrite(STREAM_ID, bufferWriter);

        // then
        assertThat(isWritten).isTrue();
        verify(sendDispatcher, times(2)).claim(any(ClaimedFragment.class), anyInt(), anyInt());
    }

    @Test
    public void shouldFailIfCannotClaimFragment()
    {
        // given
        final Dispatcher sendDispatcher = dispatcherOn(STREAM_ID, sendBuffer).thatDoes().fail().done();

        final SingleMessageWriter writer = new SingleMessageWriter(sendDispatcher);
        final DirectBufferWriter bufferWriter = new DirectBufferWriter();
        bufferWriter.wrap(BUFFER, 1, BUFFER.capacity() - 1);

        // when
        final boolean isWritten = writer.tryWrite(STREAM_ID, bufferWriter);

        // then
        assertThat(isWritten).isFalse();
    }

}

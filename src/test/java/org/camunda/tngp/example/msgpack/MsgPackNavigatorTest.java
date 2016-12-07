package org.camunda.tngp.example.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.example.msgpack.impl.JsonPathOperator;
import org.camunda.tngp.example.msgpack.impl.MsgPackNavigator;
import org.junit.Test;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;

public class MsgPackNavigatorTest
{

    @Test
    public void shouldIterateStrings() throws IOException
    {
        // given
        DirectBuffer buffer = encodeMsgPack((p) ->
        {
            p.packString("foo");
            p.packString("bar");
        });

        MsgPackNavigator navigator = new MsgPackNavigator();
        StringCollectingOperator stringCollector = new StringCollectingOperator();

        // when
        navigator.wrap(buffer, 0, buffer.capacity());
        navigator.matches(stringCollector);
        navigator.next();
        navigator.matches(stringCollector);

        // then
        assertThat(stringCollector.collectedStrings).containsExactly("foo", "bar");
    }

    @Test
    public void shouldIterateObject() throws IOException
    {
        // given

        DirectBuffer buffer = encodeMsgPack((p) ->
        {
            p.packMapHeader(2);
            p.packString("foo");
            p.packString("bar");
            p.packString("baz");
            p.packString("camunda");
        });

        MsgPackNavigator navigator = new MsgPackNavigator();
        StringCollectingOperator stringCollector = new StringCollectingOperator();

        // when
        navigator.wrap(buffer, 0, buffer.capacity());
        navigator.stepInto();
        do
        {
            navigator.matches(stringCollector);
        } while (navigator.next());

        // then
        assertThat(stringCollector.collectedStrings).containsExactly("foo", "bar", "baz", "camunda");
    }

    protected static DirectBuffer encodeMsgPack(CheckedConsumer<MessageBufferPacker> msgWriter)
    {
        MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
        try
        {
            msgWriter.accept(packer);
            packer.close();
        } catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        return new UnsafeBuffer(packer.toByteArray());
    }

    protected static class StringCollectingOperator implements JsonPathOperator
    {

        protected List<String> collectedStrings = new ArrayList<>();

        @Override
        public boolean matchesString(MsgPackNavigator context, DirectBuffer buffer, int offset, int length)
        {
            byte[] bytes = new byte[length];
            buffer.getBytes(offset, bytes);

            collectedStrings.add(new String(bytes, StandardCharsets.UTF_8));
            return false;
        }
    }

    @FunctionalInterface
    protected static interface CheckedConsumer<T>
    {
        void accept(T t) throws Exception;
    }

}

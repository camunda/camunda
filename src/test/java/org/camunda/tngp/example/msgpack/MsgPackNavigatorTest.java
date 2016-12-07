//package org.camunda.tngp.example.msgpack;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//import java.io.IOException;
//import java.nio.charset.StandardCharsets;
//import java.util.ArrayList;
//import java.util.List;
//
//import org.agrona.DirectBuffer;
//import org.camunda.tngp.example.msgpack.impl.MsgPackNavigator;
//import org.camunda.tngp.example.msgpack.impl.MsgPackValueVisitor;
//import org.junit.Test;
//
//public class MsgPackNavigatorTest
//{
//
//    @Test
//    public void shouldIterateStrings() throws IOException
//    {
//        // given
//        DirectBuffer buffer = MsgPackUtil.encodeMsgPack((p) ->
//        {
//            p.packString("foo");
//            p.packString("bar");
//        });
//
//        MsgPackNavigator navigator = new MsgPackNavigator();
//        StringCollectingOperator stringCollector = new StringCollectingOperator();
//
//        // when
//        navigator.wrap(buffer, 0, buffer.capacity());
//        navigator.matches(stringCollector);
//        navigator.next();
//        navigator.matches(stringCollector);
//
//        // then
//        assertThat(stringCollector.collectedStrings).containsExactly("foo", "bar");
//    }
//
//    @Test
//    public void shouldIterateMapElements() throws IOException
//    {
//        // given
//        DirectBuffer buffer = MsgPackUtil.encodeMsgPack((p) ->
//        {
//            p.packMapHeader(2);
//            p.packString("foo");
//            p.packString("bar");
//            p.packString("baz");
//            p.packString("camunda");
//        });
//
//        MsgPackNavigator navigator = new MsgPackNavigator();
//        StringCollectingOperator stringCollector = new StringCollectingOperator();
//
//        // when
//        navigator.wrap(buffer, 0, buffer.capacity());
//        navigator.stepInto();
//        do
//        {
//            navigator.matches(stringCollector);
//        } while (navigator.next());
//
//        // then
//        assertThat(stringCollector.collectedStrings).containsExactly("foo", "bar", "baz", "camunda");
//    }
//
//    @Test
//    public void shouldIterateMaps()
//    {
//        // given
//        DirectBuffer buffer = MsgPackUtil.encodeMsgPack((p) ->
//        {
//            p.packMapHeader(1);
//            p.packString("foo");
//            p.packString("bar");
//            p.packMapHeader(1);
//            p.packString("baz");
//            p.packString("camunda");
//        });
//
//        MsgPackNavigator navigator = new MsgPackNavigator();
//        ObjectCounter objectCounter = new ObjectCounter();
//
//        // when
//        navigator.wrap(buffer, 0, buffer.capacity());
//        navigator.stepInto();
//
//        do
//        {
//            navigator.matches(objectCounter);
//        } while (navigator.next());
//
//        // then
//        assertThat(objectCounter.visitedObjects).isEqualTo(2);
//    }
//
//
//
//    protected static class StringCollectingOperator implements MsgPackValueVisitor
//    {
//
//        protected List<String> collectedStrings = new ArrayList<>();
//
//        @Override
//        public void visitString(MsgPackNavigator context, DirectBuffer buffer, int offset, int length)
//        {
//            byte[] bytes = new byte[length];
//            buffer.getBytes(offset, bytes);
//
//            collectedStrings.add(new String(bytes, StandardCharsets.UTF_8));
//        }
//    }
//
//    protected static class ObjectCounter implements MsgPackValueVisitor
//    {
//        protected int visitedObjects = 0;
//
//
//        @Override
//        public void visitMap(MsgPackNavigator context)
//        {
//            visitedObjects++;
//        }
//    }
//
//
//}

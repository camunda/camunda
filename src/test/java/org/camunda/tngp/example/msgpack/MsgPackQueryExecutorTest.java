package org.camunda.tngp.example.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Stack;

import org.agrona.DirectBuffer;
import org.camunda.tngp.example.msgpack.impl.ByteUtil;
import org.camunda.tngp.example.msgpack.impl.ImmutableIntList;
import org.camunda.tngp.example.msgpack.impl.MsgPackType;
import org.camunda.tngp.example.msgpack.impl.newidea.ArrayIndexFilter;
import org.camunda.tngp.example.msgpack.impl.newidea.ContainerContext;
import org.camunda.tngp.example.msgpack.impl.newidea.MapValueWithKeyFilter;
import org.camunda.tngp.example.msgpack.impl.newidea.MsgPackFilter;
import org.camunda.tngp.example.msgpack.impl.newidea.MsgPackQueryExecutor;
import org.camunda.tngp.example.msgpack.impl.newidea.MsgPackToken;
import org.camunda.tngp.example.msgpack.impl.newidea.MsgPackTokenVisitor;
import org.camunda.tngp.example.msgpack.impl.newidea.RootCollectionFilter;
import org.junit.Test;

public class MsgPackQueryExecutorTest
{

    @Test
    public void testQuerySingleResult()
    {
        // given
        MsgPackFilter[] filters = new MsgPackFilter[2];
        filters[0] = new RootCollectionFilter();
        filters[1] = new MapKeyFilter("foo");
        MsgPackTokenVisitor valueVisitor = new MsgPackTokenVisitor(filters);
        MsgPackQueryExecutor executor = new MsgPackQueryExecutor(valueVisitor);

        DirectBuffer encodedMessage = MsgPackUtil.encodeMsgPack((p) ->
        {
            p.packMapHeader(2);
            p.packString("baz");
            p.packString("foo");
            p.packString("foo");
            p.packString("baz");
        });

        // when
        executor.wrap(encodedMessage, 0, encodedMessage.capacity());
        executor.traverse();

        // then
        ImmutableIntList positions = valueVisitor.getMatchingPositions();
        assertThat(positions.getSize()).isEqualTo(2);
        assertThat(positions.get(0)).isEqualTo(9);
        assertThat(positions.get(1)).isEqualTo(13);
    }

    @Test
    public void testQueryMultipleResult()
    {
        // given
        MsgPackFilter[] filters = new MsgPackFilter[2];
        filters[0] = new RootCollectionFilter();
        filters[1] = new MapKeyFilter("foo");
        MsgPackTokenVisitor valueVisitor = new MsgPackTokenVisitor(filters);
        MsgPackQueryExecutor executor = new MsgPackQueryExecutor(valueVisitor);

        DirectBuffer encodedMessage = MsgPackUtil.encodeMsgPack((p) ->
        {
            p.packMapHeader(3);  // 1
            p.packString("baz"); // 4
            p.packString("foo"); // 4
            p.packString("foo"); // 4
            p.packString("baz"); // 4
            p.packString("foo"); // 4
            p.packString("baz"); // 4
        });

        // when
        executor.wrap(encodedMessage, 0, encodedMessage.capacity());
        executor.traverse();

        // then
        ImmutableIntList positions = valueVisitor.getMatchingPositions();
        assertThat(positions.getSize()).isEqualTo(4);
        assertThat(positions.get(0)).isEqualTo(9);
        assertThat(positions.get(1)).isEqualTo(13);
        assertThat(positions.get(2)).isEqualTo(17);
        assertThat(positions.get(3)).isEqualTo(21);
    }

    @Test
    public void testNestedQuery()
    {
        // given
        MsgPackFilter[] filters = new MsgPackFilter[4];
        filters[0] = new RootCollectionFilter();
        filters[1] = new MapValueWithKeyFilter("foo".getBytes(StandardCharsets.UTF_8));
        filters[2] = new ArrayIndexFilter(1);
        filters[3] = new MapValueWithKeyFilter("bar".getBytes(StandardCharsets.UTF_8));
        MsgPackTokenVisitor valueVisitor = new MsgPackTokenVisitor(filters);
        MsgPackQueryExecutor executor = new MsgPackQueryExecutor(valueVisitor);

        DirectBuffer encodedMessage = MsgPackUtil.encodeMsgPack((p) ->
        {
            p.packMapHeader(2);                             // 1
                p.packString("NOT_THE_TARGET");             // 15
                p.packString("NOT_THE_TARGET");             // 15
                p.packString("foo");                        // 4
                p.packArrayHeader(2);                       // 1
                    p.packString("NOT_THE_TARGET");         // 15
                    p.packMapHeader(2);                     // 1
                        p.packString("NOT_THE_TARGET");     // 15
                        p.packString("NOT_THE_TARGET");     // 15
                        p.packString("bar");                // 4
                        p.packString("THE_TARGET");         // 11
        });

        System.out.println(encodedMessage.capacity());

        // when
        executor.wrap(encodedMessage, 0, encodedMessage.capacity());
        executor.traverse();

        // then
        ImmutableIntList positions = valueVisitor.getMatchingPositions();
        assertThat(positions.getSize()).isEqualTo(2);
        assertThat(positions.get(0)).isEqualTo(86); // from
        assertThat(positions.get(1)).isEqualTo(97); // to
    }

    @Test
    public void testQueryMatchingMap()
    {
        // given
        MsgPackFilter[] filters = new MsgPackFilter[2];
        filters[0] = new RootCollectionFilter();
        filters[1] = new MapValueWithKeyFilter("target".getBytes(StandardCharsets.UTF_8));
        MsgPackTokenVisitor valueVisitor = new MsgPackTokenVisitor(filters);
        MsgPackQueryExecutor executor = new MsgPackQueryExecutor(valueVisitor);

        DirectBuffer encodedMessage = MsgPackUtil.encodeMsgPack((p) ->
        {
            p.packMapHeader(2);              // 1
                p.packString("foo");         // 4
                p.packString("foo");         // 4
                p.packString("target");      // 7
                p.packMapHeader(1);          // 1
                    p.packString("foo");     // 4
                    p.packString("foo");     // 4
        });

        System.out.println(encodedMessage.capacity());

        // when
        executor.wrap(encodedMessage, 0, encodedMessage.capacity());
        executor.traverse();

        // then
        ImmutableIntList positions = valueVisitor.getMatchingPositions();
        assertThat(positions.getSize()).isEqualTo(2);
        assertThat(positions.get(0)).isEqualTo(16); // from
        assertThat(positions.get(1)).isEqualTo(25); // to
    }

    protected static class MapKeyFilter implements MsgPackFilter
    {
        protected byte[] keyword;

        public MapKeyFilter(String key)
        {
            keyword = key.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public boolean matches(Stack<ContainerContext> ctx, MsgPackToken value)
        {
            ContainerContext context = ctx.isEmpty() ? null : ctx.peek();
            // TODO: check if map (and no array)
            if (context.getCurrentElement() % 2 == 0) // => map key has odd index
            {
                if (value.getType() == MsgPackType.STRING)
                {
                    DirectBuffer encodedString = value.getValueBuffer();
                    return ByteUtil.equal(keyword, encodedString, 0, encodedString.capacity());
                }
            }

            return false;
        }

    }
}

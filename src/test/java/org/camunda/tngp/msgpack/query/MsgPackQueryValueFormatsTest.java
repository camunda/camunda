package org.camunda.tngp.msgpack.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.agrona.DirectBuffer;
import org.camunda.tngp.msgpack.filter.MapValueWithKeyFilter;
import org.camunda.tngp.msgpack.filter.MsgPackFilter;
import org.camunda.tngp.msgpack.filter.RootCollectionFilter;
import org.camunda.tngp.msgpack.util.ByteUtil;
import org.camunda.tngp.msgpack.util.MsgPackUtil;
import org.camunda.tngp.msgpack.util.MsgPackUtil.CheckedConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.msgpack.core.MessagePacker;

@RunWith(Parameterized.class)
public class MsgPackQueryValueFormatsTest
{

    @Parameters
    public static Iterable<Object[]> data()
    {
        return Arrays.asList(new Object[][] {
            { function((p) -> p.packString("foo")) },
            { function((p) -> p.packBoolean(true)) },
            { function((p) -> p.packBoolean(false)) },
            { function((p) -> p.packDouble(1.444d)) },
            { function((p) -> p.packFloat(1.555f)) },
            { function((p) -> p.packLong(longOfLength(5))) },   // <= 7 bit positive fixnum
            { function((p) -> p.packLong(-longOfLength(3))) },  // <= 5 bit negative fixnum
            { function((p) -> p.packLong(longOfLength(8))) },   // <= 8 bit unsigned int
            { function((p) -> p.packLong(longOfLength(15))) },  // <= 16 bit unsigned int
            { function((p) -> p.packLong(longOfLength(30))) },  // <= 32 bit unsigned int
            { function((p) -> p.packLong(((long) Integer.MAX_VALUE) + 10L)) },
            { function((p) -> p.packNil()) },
            { function((p) -> p.packShort((short) 123)) }
        });
    }

    // TODO: test different String types (str8, str16, str32)
    // TODO: test different map and array types

    // helping the compiler with recognizing lamdas
    protected static CheckedConsumer<MessagePacker> function(CheckedConsumer<MessagePacker> arg)
    {
        return arg;
    }

    protected static long longOfLength(int bits)
    {
        return 1L << (bits - 1);
    }


    @Parameter
    public CheckedConsumer<MessagePacker> valueWriter;

    @Test
    public void testValueQuery()
    {
        // given
        final DirectBuffer buffer = MsgPackUtil.encodeMsgPack((p) ->
        {
            p.packMapHeader(1);
            p.packString("foo");
            valueWriter.accept(p);
        });

        final MsgPackFilter[] filters = new MsgPackFilter[2];
        filters[0] = new RootCollectionFilter();
        filters[1] = new MapValueWithKeyFilter();

        final MsgPackFilterContext filterInstances = MsgPackUtil.generateDefaultInstances(0, 1);
        MapValueWithKeyFilter.encodeDynamicContext(filterInstances.dynamicContext(), "foo");

        final MsgPackTokenVisitor valueVisitor = new MsgPackTokenVisitor(filters, filterInstances);
        final MsgPackTraverser traverser = new MsgPackTraverser();
        traverser.wrap(buffer, 0, buffer.capacity());

        // when
        traverser.traverse(valueVisitor);

        // then
        assertThat(valueVisitor.numResults()).isEqualTo(1);

        valueVisitor.moveToResult(0);
        final int resultStart = valueVisitor.currentResultPosition();
        final int resultLength = valueVisitor.currentResultLength();

        final DirectBuffer expectedValue = MsgPackUtil.encodeMsgPack((p) ->
        {
            valueWriter.accept(p);
        });

        assertThat(ByteUtil.equal(
                buffer, resultStart, resultLength,
                expectedValue, 0, expectedValue.capacity()))
            .isTrue();
    }

}

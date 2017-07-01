package io.zeebe.msgpack.filter;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import io.zeebe.msgpack.query.MsgPackTraversalContext;
import io.zeebe.msgpack.spec.MsgPackToken;

public class ArrayIndexFilter implements MsgPackFilter
{

    @Override
    public boolean matches(MsgPackTraversalContext ctx, DirectBuffer filterContext, MsgPackToken value)
    {
        final int queryIndex = filterContext.getInt(0);
        return !ctx.isMap() && queryIndex == ctx.currentElement();
    }

    public static void encodeDynamicContext(MutableDirectBuffer contextBuffer, int arrayIndex)
    {
        contextBuffer.putInt(0, arrayIndex);
    }
}

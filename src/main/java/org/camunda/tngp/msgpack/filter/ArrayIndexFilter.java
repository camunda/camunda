package org.camunda.tngp.msgpack.filter;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.msgpack.query.MsgPackTraversalContext;
import org.camunda.tngp.msgpack.spec.MsgPackToken;

public class ArrayIndexFilter implements MsgPackFilter
{

    @Override
    public boolean matches(MsgPackTraversalContext ctx, DirectBuffer filterContext, MsgPackToken value)
    {
        int queryIndex = filterContext.getInt(0);
        return !ctx.isMap() && queryIndex == ctx.currentElement();
    }

    public static void encodeDynamicContext(MutableDirectBuffer contextBuffer, int arrayIndex)
    {
        contextBuffer.putInt(0, arrayIndex);
    }
}

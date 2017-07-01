package io.zeebe.msgpack.filter;

import org.agrona.DirectBuffer;
import io.zeebe.msgpack.query.MsgPackTraversalContext;
import io.zeebe.msgpack.spec.MsgPackToken;

public class WildcardFilter implements MsgPackFilter
{

    @Override
    public boolean matches(MsgPackTraversalContext ctx, DirectBuffer filterContext, MsgPackToken value)
    {
        if (ctx.hasElements() && ctx.isMap())
        {
            return ctx.currentElement() % 2 != 0; // don't match map keys
        }

        return true;
    }

}

package org.camunda.tngp.msgpack.filter;

import org.agrona.DirectBuffer;
import org.camunda.tngp.msgpack.query.MsgPackTraversalContext;
import org.camunda.tngp.msgpack.spec.MsgPackToken;

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

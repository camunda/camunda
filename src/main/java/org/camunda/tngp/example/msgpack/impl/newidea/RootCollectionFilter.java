package org.camunda.tngp.example.msgpack.impl.newidea;

import org.agrona.DirectBuffer;

public class RootCollectionFilter implements MsgPackFilter
{

    @Override
    public boolean matches(MsgPackTraversalContext ctx, DirectBuffer filterContext, MsgPackToken value)
    {
        return !ctx.hasElements() && !value.getType().isScalar();
    }

}

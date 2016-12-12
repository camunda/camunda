package org.camunda.tngp.msgpack.filter;

import org.agrona.DirectBuffer;
import org.camunda.tngp.msgpack.query.MsgPackTraversalContext;
import org.camunda.tngp.msgpack.spec.MsgPackToken;

public class RootCollectionFilter implements MsgPackFilter
{

    @Override
    public boolean matches(MsgPackTraversalContext ctx, DirectBuffer filterContext, MsgPackToken value)
    {
        return !ctx.hasElements() && !value.getType().isScalar();
    }

}

package io.zeebe.msgpack.filter;

import org.agrona.DirectBuffer;
import io.zeebe.msgpack.query.MsgPackTraversalContext;
import io.zeebe.msgpack.spec.MsgPackToken;

public class RootCollectionFilter implements MsgPackFilter
{

    @Override
    public boolean matches(MsgPackTraversalContext ctx, DirectBuffer filterContext, MsgPackToken value)
    {
        return !ctx.hasElements() && !value.getType().isScalar();
    }

}

package io.zeebe.msgpack.filter;

import org.agrona.DirectBuffer;
import io.zeebe.msgpack.query.MsgPackTraversalContext;
import io.zeebe.msgpack.spec.MsgPackToken;

public interface MsgPackFilter
{

    /**
     * If the context has elements, the cursor is guaranteed to be on the last element when this method is invoked.
     */
    boolean matches(MsgPackTraversalContext ctx, DirectBuffer filterContext, MsgPackToken value);
}

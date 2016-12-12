package org.camunda.tngp.msgpack.filter;

import org.agrona.DirectBuffer;
import org.camunda.tngp.msgpack.query.MsgPackTraversalContext;
import org.camunda.tngp.msgpack.spec.MsgPackToken;

public interface MsgPackFilter
{

    /**
     * If the context has elements, the cursor is guaranteed to be on the last element when this method is invoked.
     */
    boolean matches(MsgPackTraversalContext ctx, DirectBuffer filterContext, MsgPackToken value);
}

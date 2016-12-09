package org.camunda.tngp.example.msgpack.impl.newidea;

public interface MsgPackFilter
{

    /**
     * If the context has elements, the cursor is guaranteed to be on the last element when this method is invoked.
     */
    boolean matches(MsgPackTraversalContext ctx, MsgPackToken value);
}

package org.camunda.tngp.example.msgpack.impl.newidea;

public class RootCollectionFilter implements MsgPackFilter
{

    @Override
    public boolean matches(MsgPackTraversalContext ctx, MsgPackToken value)
    {
        return !ctx.hasElements() && !value.getType().isScalar();
    }

}

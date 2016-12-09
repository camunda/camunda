package org.camunda.tngp.example.msgpack.impl.newidea;

public class ArrayIndexFilter implements MsgPackFilter
{

    protected int queryIndex;

    public ArrayIndexFilter(int queryIndex)
    {
        this.queryIndex = queryIndex;
    }

    @Override
    public boolean matches(MsgPackTraversalContext ctx, MsgPackToken value)
    {
        return !ctx.isMap() && queryIndex == ctx.currentElement();
    }

}

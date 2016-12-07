package org.camunda.tngp.example.msgpack.impl.newidea;

import java.util.Stack;

public class ArrayIndexFilter implements MsgPackFilter
{

    protected int queryIndex;

    public ArrayIndexFilter(int queryIndex)
    {
        this.queryIndex = queryIndex;
    }

    @Override
    public boolean matches(Stack<ContainerContext> ctx, MsgPackToken value)
    {
        ContainerContext parent = ctx.isEmpty() ? null : ctx.peek();
        if (!parent.isMap() && queryIndex == parent.currentElement)
        {
            return true;
        }
        else
        {
            return false;
        }

    }

}

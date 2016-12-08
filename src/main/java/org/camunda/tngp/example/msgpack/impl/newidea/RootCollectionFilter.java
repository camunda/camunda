package org.camunda.tngp.example.msgpack.impl.newidea;

import java.util.Stack;

public class RootCollectionFilter implements MsgPackFilter
{

    @Override
    public boolean matches(Stack<ContainerContext> ctx, MsgPackToken value)
    {
        return ctx.isEmpty() && !value.getType().isScalar();
    }

}

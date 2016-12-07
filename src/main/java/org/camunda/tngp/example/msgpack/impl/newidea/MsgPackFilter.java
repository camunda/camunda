package org.camunda.tngp.example.msgpack.impl.newidea;

import java.util.Stack;

public interface MsgPackFilter
{

    boolean matches(Stack<ContainerContext> ctx, MsgPackToken value);
}

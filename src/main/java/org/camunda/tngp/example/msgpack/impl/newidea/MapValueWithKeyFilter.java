package org.camunda.tngp.example.msgpack.impl.newidea;

import java.util.Stack;

import org.camunda.tngp.example.msgpack.impl.ByteUtil;
import org.camunda.tngp.example.msgpack.impl.MsgPackType;

/**
 * Only works for maps that have scalar values as keys
 */
public class MapValueWithKeyFilter implements MsgPackFilter
{
    public static final int NO_MATCHING_VALUE = -1;
    protected int matchingValueIndex;

    protected byte[] queryKey;

    public MapValueWithKeyFilter(byte[] queryKey)
    {
        this.queryKey = queryKey;
        reset();
    }

    protected void reset()
    {
        matchingValueIndex = NO_MATCHING_VALUE;
    }

    @Override
    public boolean matches(Stack<ContainerContext> ctx, MsgPackToken value)
    {
        ContainerContext parent = ctx.isEmpty() ? null : ctx.peek();
        if (parent != null)
        {
            if (parent.isMap() && parent.currentElement == matchingValueIndex)
            {
                reset();
                return true;
            }
            if (isMapKey(parent, parent.currentElement) &&
                    value.getType() == MsgPackType.STRING &&
                    ByteUtil.equal(queryKey, value.getValueBuffer(), 0, value.getValueBuffer().capacity()))
            {
                matchingValueIndex = parent.currentElement + 1;
            }
        }

        return false;
    }

    protected boolean isMapKey(ContainerContext container, int valueIndex)
    {
        return container.isMap() && valueIndex % 2 == 0;
    }

}

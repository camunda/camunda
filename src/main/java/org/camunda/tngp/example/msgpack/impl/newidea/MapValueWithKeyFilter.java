package org.camunda.tngp.example.msgpack.impl.newidea;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.example.msgpack.impl.ByteUtil;
import org.camunda.tngp.example.msgpack.impl.MsgPackType;

/**
 * Only works for maps that have scalar values as keys
 */
public class MapValueWithKeyFilter implements MsgPackFilter
{
    public static final int NO_MATCHING_VALUE = -1;
    protected int matchingValueIndex;

    protected UnsafeBuffer queryBuffer = new UnsafeBuffer(0, 0);

    public MapValueWithKeyFilter(DirectBuffer queryKeyBuffer, int offset, int length)
    {
        this();
        this.queryBuffer.wrap(queryKeyBuffer, offset, length);
    }

    public MapValueWithKeyFilter(byte[] bytes)
    {
        this();
        this.queryBuffer.wrap(bytes);

    }

    protected MapValueWithKeyFilter()
    {
        reset();
    }

    protected void reset()
    {
        matchingValueIndex = NO_MATCHING_VALUE;
    }

    @Override
    public boolean matches(MsgPackTraversalContext ctx, MsgPackToken value)
    {
        if (ctx.hasElements())
        {
            if (ctx.isMap() && ctx.currentElement() == matchingValueIndex)
            {
                reset();
                return true;
            }
            if (ctx.isMap() &&
                    ctx.currentElement() % 2 == 0 && // map keys have even positions
                    value.getType() == MsgPackType.STRING &&
                    ByteUtil.equal(
                            queryBuffer, 0, queryBuffer.capacity(),
                            value.getValueBuffer(), 0, value.getValueBuffer().capacity()))
            {
                matchingValueIndex = ctx.currentElement() + 1;
            }
        }

        return false;
    }
}

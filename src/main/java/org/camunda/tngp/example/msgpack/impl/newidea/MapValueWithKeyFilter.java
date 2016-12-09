package org.camunda.tngp.example.msgpack.impl.newidea;

import java.nio.charset.StandardCharsets;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.example.msgpack.impl.ByteUtil;
import org.camunda.tngp.example.msgpack.impl.MsgPackType;

/**
 * Only works for maps that have scalar values as keys
 */
public class MapValueWithKeyFilter implements MsgPackFilter
{
    public static final int NO_MATCHING_VALUE = -1;

    @Override
    public boolean matches(MsgPackTraversalContext ctx, DirectBuffer filterContext, MsgPackToken value)
    {

        if (ctx.hasElements() && ctx.isMap())
        {
            MutableDirectBuffer dynamicContext = ctx.dynamicContext();

            int currentElement = ctx.currentElement();
            if (currentElement == 0)
            {
                // initialization
                dynamicContext.putInt(0, NO_MATCHING_VALUE);
            }

            int matchingValueIndex = dynamicContext.getInt(0);
            int queryLength = filterContext.getInt(0);

            if (currentElement == matchingValueIndex)
            {
                dynamicContext.putInt(0, NO_MATCHING_VALUE);
                return true;
            }
            if (ctx.currentElement() % 2 == 0 && // map keys have even positions
                    value.getType() == MsgPackType.STRING &&
                    ByteUtil.equal(
                            filterContext, BitUtil.SIZE_OF_INT, queryLength,
                            value.getValueBuffer(), 0, value.getValueBuffer().capacity()))
            {
                dynamicContext.putInt(0, currentElement + 1);
            }
        }

        return false;
    }

    public static void encodeDynamicContext(MutableDirectBuffer contextBuffer, DirectBuffer keyBuffer, int keyOffset, int keyLength)
    {
        contextBuffer.putInt(0, keyLength);
        contextBuffer.putBytes(BitUtil.SIZE_OF_INT, keyBuffer, keyOffset, keyLength);
    }

    public static void encodeDynamicContext(MutableDirectBuffer contextBuffer, String key)
    {
        UnsafeBuffer keyBuffer = new UnsafeBuffer(key.getBytes(StandardCharsets.UTF_8));
        encodeDynamicContext(contextBuffer, keyBuffer, 0, keyBuffer.capacity());
    }
}

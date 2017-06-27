package io.zeebe.broker.util.payload;

import org.agrona.DirectBuffer;

import io.zeebe.msgpack.spec.MsgPackCodes;
import io.zeebe.msgpack.spec.MsgPackFormat;
import io.zeebe.msgpack.spec.MsgPackType;

/**
 *
 */
public final class PayloadUtil
{
    public static boolean isValidPayload(DirectBuffer payload)
    {
        boolean isValid = payload.capacity() > 0;
        if (isValid)
        {
            final byte b = payload.getByte(0);
            final MsgPackFormat format = MsgPackFormat.valueOf(b);
            isValid = format.getType() == MsgPackType.MAP;
        }
        return isValid;
    }

    public static boolean isNilPayload(DirectBuffer taskPayload)
    {
        return taskPayload.capacity() == 1 && taskPayload.getByte(0) == MsgPackCodes.NIL;
    }

}

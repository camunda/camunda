/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.util;

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

    public static boolean isNilPayload(DirectBuffer jobPayload)
    {
        return jobPayload.capacity() == 1 && jobPayload.getByte(0) == MsgPackCodes.NIL;
    }

}

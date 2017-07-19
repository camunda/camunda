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
package io.zeebe.broker.clustering.raft.entry;

import io.zeebe.msgpack.UnpackedObject;
import org.agrona.DirectBuffer;

import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.StringProperty;

public class ConfiguredMember extends UnpackedObject
{
    protected StringProperty hostProp = new StringProperty("host");
    protected IntegerProperty portProp = new IntegerProperty("port");

    public ConfiguredMember()
    {
        declareProperty(hostProp);
        declareProperty(portProp);
    }

    public DirectBuffer getHost()
    {
        return hostProp.getValue();
    }

    public ConfiguredMember setHost(DirectBuffer buf)
    {
        return setHost(buf, 0, buf.capacity());
    }

    public ConfiguredMember setHost(DirectBuffer buf, int offset, int length)
    {
        hostProp.setValue(buf, offset, length);
        return this;
    }

    public int getPort()
    {
        return portProp.getValue();
    }

    public ConfiguredMember setPort(int port)
    {
        portProp.setValue(port);
        return this;
    }
}

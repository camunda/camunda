/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.raft.event;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.transport.SocketAddress;
import org.agrona.DirectBuffer;

public class RaftConfigurationMember extends UnpackedObject
{
    protected StringProperty hostProp = new StringProperty("host");
    protected IntegerProperty portProp = new IntegerProperty("port");

    public RaftConfigurationMember()
    {
        declareProperty(hostProp);
        declareProperty(portProp);
    }

    public DirectBuffer getHost()
    {
        return hostProp.getValue();
    }

    public RaftConfigurationMember setHost(final DirectBuffer buffer, final int offset, final int length)
    {
        hostProp.setValue(buffer, offset, length);
        return this;
    }

    public int getPort()
    {
        return portProp.getValue();
    }

    public RaftConfigurationMember setPort(final int port)
    {
        portProp.setValue(port);
        return this;
    }

    public RaftConfigurationMember setSocketAddress(final SocketAddress address)
    {
        setHost(address.getHostBuffer(), 0, address.hostLength());
        setPort(address.port());

        return this;
    }

    public SocketAddress getSocketAddress()
    {
        final SocketAddress socketAddress = new SocketAddress();
        final DirectBuffer host = getHost();
        socketAddress.host(host, 0, host.capacity());
        socketAddress.port(getPort());
        return socketAddress;
    }

}

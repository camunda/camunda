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
package io.zeebe.broker.transport.cfg;


import io.zeebe.broker.system.ComponentConfiguration;
import io.zeebe.transport.SocketAddress;

public class SocketBindingCfg extends ComponentConfiguration
{
    public String host;
    public int port = -1;
    public int receiveBufferSize = -1;
    public int sendBufferSize = -1;
    public long controlMessageRequestTimeoutInMillis = 10_000;

    public String getHost(String defaultValue)
    {
        return getOrDefault(host, defaultValue);
    }

    public int getPort()
    {
        return port;
    }

    public int getReceiveBufferSize(int defaultValue)
    {
        return getBufferSize(this.receiveBufferSize, defaultValue);
    }

    public int getSendBufferSize(int defaultValue)
    {
        return getBufferSize(this.sendBufferSize, defaultValue);
    }

    protected int getBufferSize(int configuredValue, int defaultValue)
    {
        int receiveBufferSize = configuredValue;
        if (receiveBufferSize == -1)
        {
            receiveBufferSize = defaultValue;
        }
        final int receiveBufferSizeInByte = receiveBufferSize * 1024 * 1024;
        return receiveBufferSizeInByte;
    }

    public long getControlMessageRequestTimeoutInMillis(long defaultValue)
    {
        long returnValue = controlMessageRequestTimeoutInMillis;
        if (returnValue  < 0)
        {
            returnValue = defaultValue;
        }
        return returnValue;
    }

    public SocketAddress toSocketAddress()
    {
        return new SocketAddress(host, port);
    }
}

package io.zeebe.broker.transport.cfg;

import io.zeebe.broker.system.ComponentConfiguration;

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
}

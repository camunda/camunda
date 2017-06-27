package io.zeebe.broker.clustering.raft.entry;

import org.agrona.DirectBuffer;

import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.broker.util.msgpack.property.IntegerProperty;
import io.zeebe.broker.util.msgpack.property.StringProperty;

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

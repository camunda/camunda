package io.zeebe.broker.clustering.raft.entry;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.broker.util.msgpack.property.ArrayProperty;
import io.zeebe.broker.util.msgpack.value.ArrayValue;
import io.zeebe.broker.util.msgpack.value.ArrayValueIterator;
import io.zeebe.msgpack.spec.MsgPackHelper;

public class ConfigurationEntry extends UnpackedObject
{
    protected static final DirectBuffer EMPTY_ARRAY = new UnsafeBuffer(MsgPackHelper.EMPTY_ARRAY);

    protected ConfiguredMember configuredMember = new ConfiguredMember();
    protected ArrayProperty<ConfiguredMember> membersProp = new ArrayProperty<>("members",
            new ArrayValue<>(),
            new ArrayValue<>(EMPTY_ARRAY, 0, EMPTY_ARRAY.capacity()),
            configuredMember);

    public ConfigurationEntry()
    {
        declareProperty(membersProp);
    }

    public ArrayValueIterator<ConfiguredMember> members()
    {
        return membersProp;
    }
}

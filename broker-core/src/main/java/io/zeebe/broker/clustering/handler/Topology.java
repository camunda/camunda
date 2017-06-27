package io.zeebe.broker.clustering.handler;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.broker.util.msgpack.property.ArrayProperty;
import io.zeebe.broker.util.msgpack.value.ArrayValue;
import io.zeebe.broker.util.msgpack.value.ArrayValueIterator;
import io.zeebe.msgpack.spec.MsgPackHelper;


public class Topology extends UnpackedObject
{
    protected static final DirectBuffer EMPTY_ARRAY = new UnsafeBuffer(MsgPackHelper.EMPTY_ARRAY);

    protected ArrayProperty<TopicLeader> topicLeadersProp = new ArrayProperty<>("topicLeaders",
        new ArrayValue<>(),
        new ArrayValue<>(EMPTY_ARRAY, 0, EMPTY_ARRAY.capacity()),
        new TopicLeader());

    protected ArrayProperty<BrokerAddress> brokersProp = new ArrayProperty<>("brokers",
        new ArrayValue<>(),
        new ArrayValue<>(EMPTY_ARRAY, 0, EMPTY_ARRAY.capacity()),
        new BrokerAddress());

    public Topology()
    {
        this
            .declareProperty(topicLeadersProp)
            .declareProperty(brokersProp);
    }

    public ArrayValueIterator<TopicLeader> topicLeaders()
    {
        return topicLeadersProp;
    }

    public ArrayProperty<BrokerAddress> brokers()
    {
        return brokersProp;
    }

}

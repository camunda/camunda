package org.camunda.tngp.client.benchmark.msgpack;

import java.util.HashMap;
import java.util.Map;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.client.benchmark.msgpack.MsgPackSerializer.Type;
import org.camunda.tngp.client.benchmark.msgpack.POJOFactory.InstantiateAlwaysFactory;
import org.camunda.tngp.client.benchmark.msgpack.POJOFactory.ReuseObjectAndPropertiesFactory;
import org.camunda.tngp.client.benchmark.msgpack.POJOFactory.ReuseObjectFactory;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class POJOSerializationContext
{
    @Param(value = {
            "REUSE_POJO_REUSE_PROPERTIES",
            "REUSE_POJO_INSTANTIATE_PROPERTIES",
            "INSTANTIATE_POJO_INSTANTIATE_PROPERTIES",
        })
    protected POJOFactory.Type pojoFactoryType;

    @Param(value = {
            "JACKSON",
            "BROKER"
        })
    protected MsgPackSerializer.Type serializerType;


    protected MsgPackSerializer serializer;
    protected POJOFactory pojoFactory;

    protected UnsafeBuffer targetBuffer = new UnsafeBuffer(new byte[1024 * 2]);



    protected Map<POJOFactory.Type, POJOFactory> jacksonPojoFactories = new HashMap<>();
    protected Map<POJOFactory.Type, POJOFactory> brokerPojoFactories = new HashMap<>();

    public POJOSerializationContext()
    {
        add(brokerPojoFactories, new ReuseObjectAndPropertiesFactory(new BrokerTaskEvent()));
        add(brokerPojoFactories, new ReuseObjectFactory(new BrokerTaskEvent()));
        add(brokerPojoFactories, new InstantiateAlwaysFactory(() -> new BrokerTaskEvent()));
        add(jacksonPojoFactories, new ReuseObjectAndPropertiesFactory(new JacksonTaskEvent()));
        add(jacksonPojoFactories, new ReuseObjectFactory(new JacksonTaskEvent()));
        add(jacksonPojoFactories, new InstantiateAlwaysFactory(() -> new JacksonTaskEvent()));
    }

    protected void add(Map<POJOFactory.Type, POJOFactory> factories, POJOFactory factory)
    {
        factories.put(factory.getType(), factory);
    }

    @Setup
    public void setUp()
    {
        if (serializerType == Type.BROKER)
        {
            serializer = new MsgPackBrokerSerializer();
            pojoFactory = brokerPojoFactories.get(pojoFactoryType);

        }
        else
        {
            serializer = new MsgPackJacksonSerializer();
            pojoFactory = jacksonPojoFactories.get(pojoFactoryType);
        }
    }

    public MsgPackSerializer getSerializer()
    {
        return serializer;
    }

    public POJOFactory getPojoFactory()
    {
        return pojoFactory;
    }

    public UnsafeBuffer getTargetBuffer()
    {
        return targetBuffer;
    }

}

package org.camunda.tngp.test.broker.protocol.brokerapi;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.camunda.tngp.test.broker.protocol.MsgPackHelper;
import org.camunda.tngp.test.util.collection.MapFactoryBuilder;

public class ExecuteCommandResponseBuilder
{

    protected final Consumer<ExecuteCommandResponseStub> registrationFunction;
    protected ExecuteCommandResponseStub stub;

    public ExecuteCommandResponseBuilder(
            Consumer<ExecuteCommandResponseStub> registrationFunction,
            MsgPackHelper msgPackConverter,
            Predicate<ExecuteCommandRequest> activationFunction)
    {
        this.registrationFunction = registrationFunction;
        this.stub = new ExecuteCommandResponseStub(msgPackConverter, activationFunction);
    }

    public ExecuteCommandResponseBuilder respondWith()
    {
        // syntactic sugar
        return this;
    }

    public ExecuteCommandResponseBuilder topicName(final String topicName)
    {
        return topicName((r) -> topicName);
    }


    public ExecuteCommandResponseBuilder topicName(Function<ExecuteCommandRequest, String> topicNameFunction)
    {
        stub.setTopicNameFunction(topicNameFunction);
        return this;
    }

    public ExecuteCommandResponseBuilder partitionId(final int partitionId)
    {
        return partitionId((r) -> partitionId);
    }


    public ExecuteCommandResponseBuilder partitionId(Function<ExecuteCommandRequest, Integer> partitionIdFunction)
    {
        stub.setPartitionIdFunction(partitionIdFunction);
        return this;
    }

    public ExecuteCommandResponseBuilder key(long l)
    {
        return key((r) -> l);
    }

    public ExecuteCommandResponseBuilder key(Function<ExecuteCommandRequest, Long> keyFunction)
    {
        stub.setKeyFunction(keyFunction);
        return this;
    }


    public ExecuteCommandResponseBuilder event(Map<String, Object> map)
    {
        stub.setEventFunction((re) -> map);
        return this;
    }

    public MapFactoryBuilder<ExecuteCommandRequest, ExecuteCommandResponseBuilder> event()
    {
        return new MapFactoryBuilder<>(this, stub::setEventFunction);
    }

    public void register()
    {
        registrationFunction.accept(stub);
    }
}

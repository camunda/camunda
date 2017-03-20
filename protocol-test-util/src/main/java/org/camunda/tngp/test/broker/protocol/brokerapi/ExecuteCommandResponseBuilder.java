package org.camunda.tngp.test.broker.protocol.brokerapi;

import java.util.Map;
import java.util.function.Consumer;
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

    public ExecuteCommandResponseBuilder topicId(int i)
    {
        return topicId((r) -> i);
    }


    public ExecuteCommandResponseBuilder topicId(Function<ExecuteCommandRequest, Integer> topicIdFunction)
    {
        stub.setTopicIdFunction(topicIdFunction);
        return this;
    }

    public ExecuteCommandResponseBuilder longKey(long l)
    {
        return longKey((r) -> l);
    }

    public ExecuteCommandResponseBuilder longKey(Function<ExecuteCommandRequest, Long> keyFunction)
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

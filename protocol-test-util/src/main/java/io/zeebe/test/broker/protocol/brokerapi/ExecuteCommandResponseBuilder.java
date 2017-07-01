package io.zeebe.test.broker.protocol.brokerapi;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.test.util.collection.MapFactoryBuilder;

public class ExecuteCommandResponseBuilder
{

    protected final Consumer<ResponseStub<ExecuteCommandRequest>> registrationFunction;
    protected final ExecuteCommandResponseWriter commandResponseWriter;
    protected final Predicate<ExecuteCommandRequest> activationFunction;

    public ExecuteCommandResponseBuilder(
            Consumer<ResponseStub<ExecuteCommandRequest>> registrationFunction,
            MsgPackHelper msgPackConverter,
            Predicate<ExecuteCommandRequest> activationFunction)
    {
        this.registrationFunction = registrationFunction;
        this.commandResponseWriter = new ExecuteCommandResponseWriter(msgPackConverter);
        this.activationFunction = activationFunction;
    }

    public ExecuteCommandResponseBuilder topicName(final String topicName)
    {
        return topicName((r) -> topicName);
    }


    public ExecuteCommandResponseBuilder topicName(Function<ExecuteCommandRequest, String> topicNameFunction)
    {
        commandResponseWriter.setTopicNameFunction(topicNameFunction);
        return this;
    }

    public ExecuteCommandResponseBuilder partitionId(final int partitionId)
    {
        return partitionId((r) -> partitionId);
    }


    public ExecuteCommandResponseBuilder partitionId(Function<ExecuteCommandRequest, Integer> partitionIdFunction)
    {
        commandResponseWriter.setPartitionIdFunction(partitionIdFunction);
        return this;
    }

    public ExecuteCommandResponseBuilder key(long l)
    {
        return key((r) -> l);
    }

    public ExecuteCommandResponseBuilder key(Function<ExecuteCommandRequest, Long> keyFunction)
    {
        commandResponseWriter.setKeyFunction(keyFunction);
        return this;
    }


    public ExecuteCommandResponseBuilder event(Map<String, Object> map)
    {
        commandResponseWriter.setEventFunction((re) -> map);
        return this;
    }

    public MapFactoryBuilder<ExecuteCommandRequest, ExecuteCommandResponseBuilder> event()
    {
        return new MapFactoryBuilder<>(this, commandResponseWriter::setEventFunction);
    }

    public void register()
    {
        registrationFunction.accept(new ResponseStub<>(activationFunction, commandResponseWriter));
    }
}

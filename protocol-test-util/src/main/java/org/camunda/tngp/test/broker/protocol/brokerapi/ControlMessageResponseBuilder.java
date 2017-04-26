package org.camunda.tngp.test.broker.protocol.brokerapi;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.camunda.tngp.test.broker.protocol.MsgPackHelper;
import org.camunda.tngp.test.util.collection.MapFactoryBuilder;

public class ControlMessageResponseBuilder
{

    protected final Consumer<ResponseStub<ControlMessageRequest>> registrationFunction;
    protected final ControlMessageResponseWriter responseWriter;
    protected final Predicate<ControlMessageRequest> activationFunction;

    public ControlMessageResponseBuilder(
            Consumer<ResponseStub<ControlMessageRequest>> registrationFunction,
            MsgPackHelper msgPackConverter,
            Predicate<ControlMessageRequest> activationFunction)
    {
        this.registrationFunction = registrationFunction;
        this.responseWriter = new ControlMessageResponseWriter(msgPackConverter);
        this.activationFunction = activationFunction;
    }

    public ControlMessageResponseBuilder respondWith()
    {
        // syntactic sugar
        return this;
    }

    public ControlMessageResponseBuilder data(Map<String, Object> map)
    {
        responseWriter.setDataFunction((re) -> map);
        return this;
    }

    public MapFactoryBuilder<ControlMessageRequest, ControlMessageResponseBuilder> data()
    {
        return new MapFactoryBuilder<>(this, responseWriter::setDataFunction);
    }

    public void register()
    {
        registrationFunction.accept(new ResponseStub<>(activationFunction, responseWriter));
    }
}

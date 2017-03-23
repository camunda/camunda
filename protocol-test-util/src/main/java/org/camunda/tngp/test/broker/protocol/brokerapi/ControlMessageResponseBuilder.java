package org.camunda.tngp.test.broker.protocol.brokerapi;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.camunda.tngp.test.broker.protocol.MsgPackHelper;
import org.camunda.tngp.test.util.collection.MapFactoryBuilder;

public class ControlMessageResponseBuilder
{

    protected final Consumer<ControlMessageResponseStub> registrationFunction;
    protected ControlMessageResponseStub stub;

    public ControlMessageResponseBuilder(
            Consumer<ControlMessageResponseStub> registrationFunction,
            MsgPackHelper msgPackConverter,
            Predicate<ControlMessageRequest> activationFunction)
    {
        this.registrationFunction = registrationFunction;
        this.stub = new ControlMessageResponseStub(msgPackConverter, activationFunction);
    }

    public ControlMessageResponseBuilder respondWith()
    {
        // syntactic sugar
        return this;
    }

    public ControlMessageResponseBuilder data(Map<String, Object> map)
    {
        stub.setDataFunction((re) -> map);
        return this;
    }

    public MapFactoryBuilder<ControlMessageRequest, ControlMessageResponseBuilder> data()
    {
        return new MapFactoryBuilder<>(this, stub::setDataFunction);
    }

    public void register()
    {
        registrationFunction.accept(stub);
    }
}

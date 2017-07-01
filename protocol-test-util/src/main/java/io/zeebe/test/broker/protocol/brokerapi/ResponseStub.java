package io.zeebe.test.broker.protocol.brokerapi;

import java.util.function.Predicate;

public class ResponseStub<R>
{

    protected final Predicate<R> activationFunction;
    protected final MessageBuilder<R> responseWriter;

    public ResponseStub(Predicate<R> activationFunction, MessageBuilder<R> responseWriter)
    {
        this.responseWriter = responseWriter;
        this.activationFunction = activationFunction;
    }

    public boolean applies(R request)
    {
        return activationFunction.test(request);
    }

    public MessageBuilder<R> getResponseWriter()
    {
        return responseWriter;
    }

}

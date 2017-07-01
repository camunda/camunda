package io.zeebe.test.broker.protocol.brokerapi;

import java.util.function.Consumer;
import java.util.function.Predicate;

import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.test.broker.protocol.MsgPackHelper;

public class ErrorResponseBuilder<R>
{
    protected final Consumer<ResponseStub<R>> registrationFunction;
    protected final ErrorResponseWriter<R> commandResponseWriter;
    protected final Predicate<R> activationFunction;

    public ErrorResponseBuilder(
            Consumer<ResponseStub<R>> registrationFunction,
            MsgPackHelper msgPackConverter,
            Predicate<R> activationFunction)
    {
        this.registrationFunction = registrationFunction;
        this.commandResponseWriter = new ErrorResponseWriter<>(msgPackConverter);
        this.activationFunction = activationFunction;
    }

    public ErrorResponseBuilder<R> errorCode(ErrorCode errorCode)
    {
        this.commandResponseWriter.setErrorCode(errorCode);
        return this;
    }

    public ErrorResponseBuilder<R> errorData(String errorData)
    {
        this.commandResponseWriter.setErrorData(errorData);
        return this;
    }

    public void register()
    {
        registrationFunction.accept(new ResponseStub<>(activationFunction, commandResponseWriter));
    }

}

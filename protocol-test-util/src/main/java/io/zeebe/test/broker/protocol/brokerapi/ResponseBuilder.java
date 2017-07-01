package io.zeebe.test.broker.protocol.brokerapi;

public class ResponseBuilder<T, E>
{

    protected final T regularResponseBuilder;
    protected final E errorResponseBuilder;

    public ResponseBuilder(
            T regularResponseBuilder,
            E errorResponseBuilder)
    {
        this.regularResponseBuilder = regularResponseBuilder;
        this.errorResponseBuilder = errorResponseBuilder;
    }

    public T respondWith()
    {
        return regularResponseBuilder;
    }

    public E respondWithError()
    {
        return errorResponseBuilder;
    }

}

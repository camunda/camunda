package org.camunda.tngp.transport;

public interface PooledFuture<T>
{

    T pollAndReturnOnSuccess();
    void resolve(T value);
    void fail();

}

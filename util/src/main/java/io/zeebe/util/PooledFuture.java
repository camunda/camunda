package io.zeebe.util;

public interface PooledFuture<T>
{

    // consumer
    T poll();
    boolean isFailed();
    void release();

    // producer
    void resolve(T value);
    void fail();

}

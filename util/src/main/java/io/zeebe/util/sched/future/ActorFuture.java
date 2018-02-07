package io.zeebe.util.sched.future;

import java.util.concurrent.Future;

import io.zeebe.util.sched.ActorJob;

/** interface for actor futures */
public interface ActorFuture<V> extends Future<V>
{
    void complete(V value);

    void completeExceptionally(String failure, Throwable throwable);

    void completeExceptionally(Throwable throwable);

    V join();

    /** To be used by scheduler only */
    boolean block(ActorJob job);
}

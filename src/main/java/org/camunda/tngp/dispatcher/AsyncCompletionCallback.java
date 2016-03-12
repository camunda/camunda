package org.camunda.tngp.dispatcher;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface AsyncCompletionCallback<T>
{

    void onComplete(Throwable t, T result);

    static <T> AsyncCompletionCallback<T> completeFuture(final CompletableFuture<T> future) {

        return new AsyncCompletionCallback<T>()
        {

            @Override
            public void onComplete(Throwable t, T result)
            {
                if(t != null)
                {
                    future.completeExceptionally(t);
                }
                else
                {
                    future.complete(result);
                }
            }
        };

    }

}

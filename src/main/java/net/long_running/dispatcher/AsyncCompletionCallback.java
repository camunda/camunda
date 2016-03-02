package net.long_running.dispatcher;

@FunctionalInterface
public interface AsyncCompletionCallback<T>
{

    void onComplete(Throwable t, T result);

}

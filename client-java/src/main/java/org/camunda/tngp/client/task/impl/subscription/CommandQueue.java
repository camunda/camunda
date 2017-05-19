package org.camunda.tngp.client.task.impl.subscription;

public interface CommandQueue<T>
{

    void add(T cmd);

    int drain();
}

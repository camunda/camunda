package org.camunda.tngp.client.task.impl;

public interface CommandQueue<T>
{

    void add(T cmd);

    int drain();
}

package org.camunda.tngp.dispatcher;

public class Dispatchers
{
    public static DispatcherBuilder create(String name)
    {
        return new DispatcherBuilder(name);
    }

}

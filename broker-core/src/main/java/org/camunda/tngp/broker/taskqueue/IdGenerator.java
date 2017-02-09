package org.camunda.tngp.broker.taskqueue;

public class IdGenerator
{

    private long lastId;

    public IdGenerator(long initialValue)
    {
        lastId = initialValue;
    }

    public long nextId()
    {
        return ++lastId;
    }

}

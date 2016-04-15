package org.camunda.tngp.log;

public class Logs
{
    public static LogBuilder createLog(String name, int id)
    {
        return new LogBuilder(name, id);
    }

}

package org.camunda.tngp.log;

public class Logs
{
    public static LogBuilder createLog(String name)
    {
        return new LogBuilder(name);
    }

}

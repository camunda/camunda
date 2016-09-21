package org.camunda.tngp.log;

public class Logs
{
    public static FsLogBuilder createFsLog(String name, int id)
    {
        return new FsLogBuilder(name, id);
    }

}

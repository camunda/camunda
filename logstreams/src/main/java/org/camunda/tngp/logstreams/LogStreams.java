package org.camunda.tngp.logstreams;

public class LogStreams
{
    public static FsLogStreamBuilder createFsLogStream(String name, int id)
    {
        return new FsLogStreamBuilder(name, id);
    }

    public static FsSnapshotStorageBuilder createFsSnapshotStore(String rootPath)
    {
        return new FsSnapshotStorageBuilder(rootPath);
    }

}

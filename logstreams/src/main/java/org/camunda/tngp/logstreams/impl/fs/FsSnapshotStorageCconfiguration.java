package org.camunda.tngp.logstreams.impl.fs;

public class FsSnapshotStorageCconfiguration
{
    String rootPath;

    public void setRootPath(String rootPath)
    {
        this.rootPath = rootPath;
    }

    public String getRootPath()
    {
        return rootPath;
    }

}
package org.camunda.tngp.logstreams;

import java.io.File;
import java.util.Objects;

import org.camunda.tngp.logstreams.impl.fs.FsSnapshotStorage;
import org.camunda.tngp.logstreams.impl.fs.FsSnapshotStorageConfiguration;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;

public class FsSnapshotStorageBuilder
{
    protected String rootPath;

    public FsSnapshotStorageBuilder(String rootPath)
    {
        this.rootPath = rootPath;
    }

    public FsSnapshotStorageBuilder rootPath(String rootPath)
    {
        this.rootPath = rootPath;
        return this;
    }

    public SnapshotStorage build()
    {
        Objects.requireNonNull(rootPath, "rootPath cannot be null");

        final File file = new File(rootPath);

        if (!file.exists())
        {
            file.mkdirs();
        }

        final FsSnapshotStorageConfiguration cfg = new FsSnapshotStorageConfiguration();
        cfg.setRootPath(rootPath);

        return new FsSnapshotStorage(cfg);
    }
}

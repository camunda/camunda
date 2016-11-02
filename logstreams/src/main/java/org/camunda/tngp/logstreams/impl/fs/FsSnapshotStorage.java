package org.camunda.tngp.logstreams.impl.fs;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.agrona.LangUtil;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;

public class FsSnapshotStorage implements SnapshotStorage
{
    protected final FsSnapshotStorageConfiguration cfg;

    public FsSnapshotStorage(FsSnapshotStorageConfiguration cfg)
    {
        this.cfg = cfg;
    }

    static long position(String fileName, String snapshotName)
    {
        return Long.parseLong(fileName.substring(snapshotName.length() + 1, fileName.length() - 5));
    }

    @Override
    public FsReadableSnapshot getLastSnapshot(String name) throws Exception
    {
        final File rootFile = new File(cfg.getRootPath());
        final List<File> snapshotFiles = Arrays.asList(rootFile.listFiles(file -> cfg.matchesSnapshotFileNamePattern(file, name)));

        FsReadableSnapshot snapshot = null;

        if (snapshotFiles.size() > 0)
        {
            snapshotFiles.sort((f1, f2) -> Long.compare(position(f1.getName(), name), position(f2.getName(), name)));

            final File snapshotFile = snapshotFiles.get(0);
            final long logPosition = position(snapshotFile.getName(), name);

            final String checksumFileName = cfg.checksumFileName(name, logPosition);
            final File checksumFile = new File(checksumFileName);

            if (checksumFile.exists())
            {
                snapshot = new FsReadableSnapshot(cfg, snapshotFile, checksumFile, logPosition);
            }
            else
            {
                // delete snapshot file since checksum does not exist anymore
                System.err.println(String.format("Delete snapshot %s, no checksum file exists.", snapshotFile.getAbsolutePath()));

                snapshotFile.delete();
            }
        }

        return snapshot;
    }

    @Override
    public FsSnapshotWriter createSnapshot(String name, long logPosition) throws Exception
    {
        final FsReadableSnapshot lastSnapshot = getLastSnapshot(name);

        final String snapshotFileName = cfg.snapshotFileName(name, logPosition);
        final String checksumFileName = cfg.checksumFileName(name, logPosition);

        final File snapshotFile = new File(snapshotFileName);
        final File checksumFile = new File(checksumFileName);

        if (snapshotFile.exists())
        {
            throw new RuntimeException(String.format("Cannot write snapshot %s, file already exists.", snapshotFile.getAbsolutePath()));
        }

        if (checksumFile.exists())
        {
            throw new RuntimeException(String.format("Cannot write snapshot checksum %s, file already exists.", checksumFile.getAbsolutePath()));
        }

        try
        {
            snapshotFile.createNewFile();
            checksumFile.createNewFile();
        }
        catch (IOException e)
        {
            checksumFile.delete();
            snapshotFile.delete();
            LangUtil.rethrowUnchecked(e);
        }

        return new FsSnapshotWriter(cfg, snapshotFile, checksumFile, lastSnapshot);
    }
}

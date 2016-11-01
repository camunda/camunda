package org.camunda.tngp.logstreams.impl.fs;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

import org.agrona.BitUtil;
import org.agrona.LangUtil;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.logstreams.spi.SnapshotWriter;

public class FsSnapshotStorage implements SnapshotStorage
{
    public static final String CHECKSUM_ALGO = "MD5";

    protected final FsSnapshotStorageCconfiguration cfg;

    public FsSnapshotStorage(FsSnapshotStorageCconfiguration cfg)
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
        final List<File> snapshotFiles = Arrays.asList(rootFile.listFiles((f) -> f.getName().startsWith(name) && f.getName().endsWith(".data")));

        FsReadableSnapshot snapshot = null;

        if (snapshotFiles.size() > 0)
        {
            snapshotFiles.sort((f1, f2) -> Long.compare(position(f1.getName(), name), position(f2.getName(), name)));

            final File snapshotFile = snapshotFiles.get(0);
            final long logPosition = position(snapshotFile.getName(), name);

            final String checksumFileName = String.format("%s%s%s-%d.md5", cfg.getRootPath(), File.separator, name, logPosition);
            final File checksumFile = new File(checksumFileName);

            if (checksumFile.exists())
            {
                final byte[] checksum = readChecksum(checksumFile);
                final InputStream dataInputStream = new BufferedInputStream(new FileInputStream(snapshotFile));
                snapshot = new FsReadableSnapshot(snapshotFile, checksumFile, logPosition, dataInputStream, checksum);
            }
            else
            {
                // delete snapshot file since checksum does not exist anymore
                snapshotFile.delete();
            }
        }

        return snapshot;
    }

    private byte[] readChecksum(File checksumFile)
    {
        byte[] checksum = null;

        try (final BufferedReader checksumReader = new BufferedReader(new InputStreamReader(new FileInputStream(checksumFile))))
        {
            final String checksumLine = checksumReader.readLine();
            final String checksumString = checksumLine.substring(0, checksumLine.indexOf(" "));
            checksum = BitUtil.fromHex(checksumString);
        }
        catch (Exception e)
        {
            LangUtil.rethrowUnchecked(e);
        }

        return checksum;
    }

    @Override
    public SnapshotWriter createSnapshot(String name, long logPosition) throws Exception
    {
        final FsReadableSnapshot lastSnapshot = getLastSnapshot(name);

        final String snapshotFileName = String.format("%s%s%s-%d.data", cfg.getRootPath(), File.separator, name, logPosition);
        final String checksumFileName = String.format("%s%s%s-%d.md5", cfg.getRootPath(), File.separator, name, logPosition);

        final File snapshotFile = new File(snapshotFileName);
        final File checksumFile = new File(checksumFileName);

        if (snapshotFile.exists())
        {
            throw new RuntimeException(String.format("Cannot write snapshot %s, file already exists.", snapshotFile.getAbsolutePath()));
        }

        if (checksumFile.exists())
        {
            throw new RuntimeException(String.format("Cannot write snapshot %s, file already exists.", checksumFile.getAbsolutePath()));
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

        return new FsSnapshotWriter(snapshotFile, checksumFile, lastSnapshot);
    }
}

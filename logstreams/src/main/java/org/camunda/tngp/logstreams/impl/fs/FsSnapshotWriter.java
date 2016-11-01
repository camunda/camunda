package org.camunda.tngp.logstreams.impl.fs;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

import org.agrona.BitUtil;
import org.agrona.LangUtil;
import org.camunda.tngp.logstreams.spi.SnapshotWriter;

public class FsSnapshotWriter implements SnapshotWriter
{
    protected File dataFile;
    protected File checksumFile;

    protected DigestOutputStream dataOutputStream;
    protected BufferedOutputStream checksumOutputStream;
    protected FsReadableSnapshot lastSnapshot;

    public FsSnapshotWriter(File snapshotFile, File checksumFile, FsReadableSnapshot lastSnapshot)
    {
        this.dataFile = snapshotFile;
        this.checksumFile = checksumFile;
        this.lastSnapshot = lastSnapshot;

        try
        {
            dataOutputStream = new DigestOutputStream(new BufferedOutputStream(new FileOutputStream(snapshotFile)),
                    MessageDigest.getInstance(FsSnapshotStorage.CHECKSUM_ALGO));
            checksumOutputStream = new BufferedOutputStream(new FileOutputStream(checksumFile));
        }
        catch (Exception e)
        {
            abort();
            LangUtil.rethrowUnchecked(e);
        }
    }

    @Override
    public OutputStream getOutputStream()
    {
        return dataOutputStream;
    }

    @Override
    public void commit() throws IOException
    {
        try
        {
            dataOutputStream.close();

            final MessageDigest digest = dataOutputStream.getMessageDigest();

            final byte[] digestBytes = digest.digest();
            final String digestString = BitUtil.toHex(digestBytes);
            final String checksumFileContents = String.format("%s %s", digestString, dataFile.getName());

            checksumOutputStream.write(checksumFileContents.getBytes(StandardCharsets.UTF_8));
            checksumOutputStream.close();

            if (lastSnapshot != null)
            {
                lastSnapshot.getDataFile().delete();
                lastSnapshot.getChecksumFile().delete();
            }
        }
        catch (Exception e)
        {
            abort();
            throw e;
        }
    }

    @Override
    public void abort()
    {
        closeSilently(dataOutputStream);
        closeSilently(checksumOutputStream);
        dataFile.delete();
        checksumFile.delete();
    }

    static void closeSilently(Closeable out)
    {
        if (out != null)
        {
            try
            {
                out.close();
            }
            catch (Exception e)
            {
                // ignore
            }
        }
    }

    public File getChecksumFile()
    {
        return checksumFile;
    }

    public File getDataFile()
    {
        return dataFile;
    }
}

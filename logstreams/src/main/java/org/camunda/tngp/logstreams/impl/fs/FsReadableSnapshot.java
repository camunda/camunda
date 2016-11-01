package org.camunda.tngp.logstreams.impl.fs;

import java.io.File;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.agrona.LangUtil;
import org.camunda.tngp.logstreams.spi.ReadableSnapshot;

public class FsReadableSnapshot implements ReadableSnapshot
{
    protected File dataFile;
    protected File checksumFile;

    protected final long position;
    protected DigestInputStream inputStream;
    protected byte[] digest;

    public FsReadableSnapshot(File dataFile, File checksumFile, long position, InputStream stream, byte[] digest)
    {
        this.dataFile = dataFile;
        this.checksumFile = checksumFile;
        this.position = position;
        this.digest = digest;

        try
        {
            this.inputStream = new DigestInputStream(stream, MessageDigest.getInstance(FsSnapshotStorage.CHECKSUM_ALGO));
        }
        catch (NoSuchAlgorithmException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    @Override
    public void validateAndClose() throws Exception
    {
        final MessageDigest messageDigest = inputStream.getMessageDigest();

        try
        {
            inputStream.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        final byte[] digestOfBytesRead = messageDigest.digest();
        final boolean digestsEqual = Arrays.equals(digestOfBytesRead, digest);

        if (!digestsEqual)
        {
            throw new Exception("Read invalid snapshot!");
        }
    }

    @Override
    public long getPosition()
    {
        return position;
    }

    @Override
    public InputStream getData()
    {
        return inputStream;
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

package org.camunda.tngp.logstreams.impl.snapshot.fs;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;

import org.agrona.BitUtil;
import org.agrona.LangUtil;
import org.camunda.tngp.logstreams.snapshot.InvalidSnapshotException;
import org.camunda.tngp.logstreams.spi.ReadableSnapshot;
import org.camunda.tngp.util.FileUtil;

public class FsReadableSnapshot implements ReadableSnapshot
{
    protected final FsSnapshotStorageConfiguration config;

    protected final File dataFile;
    protected final File checksumFile;

    protected final long position;

    protected byte[] checksum;
    protected DigestInputStream inputStream;

    public FsReadableSnapshot(FsSnapshotStorageConfiguration config, File dataFile, File checksumFile, long position)
    {
        this.config = config;
        this.dataFile = dataFile;
        this.checksumFile = checksumFile;
        this.position = position;

        tryInit();
    }

    protected void tryInit()
    {
        try
        {
            this.inputStream = initDataInputStream();

            final String checksumFileContent = readChecksumContent(checksumFile);

            this.checksum = extractCheckum(checksumFileContent);
            final String dataFileName = extractDataFileName(checksumFileContent);

            if (!dataFileName.equals(dataFile.getName()))
            {
                throw new RuntimeException("Read invalid snapshot, file name doesn't match.");
            }
        }
        catch (Exception e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    protected DigestInputStream initDataInputStream() throws Exception
    {
        final MessageDigest messageDigest = MessageDigest.getInstance(config.getChecksumAlgorithm());

        final FileInputStream fileInputStream = new FileInputStream(dataFile);
        final BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);

        return new DigestInputStream(bufferedInputStream, messageDigest);
    }

    protected String readChecksumContent(File checksumFile) throws IOException
    {
        final String checksumLine;

        try (
            final FileInputStream fileInputStream = new FileInputStream(checksumFile);
            final InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
            final BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        )
        {
            checksumLine = bufferedReader.readLine();

            if (checksumLine == null || checksumLine.isEmpty())
            {
                throw new RuntimeException("Read invalid checksum file, no content");
            }
        }

        return checksumLine;
    }

    protected byte[] extractCheckum(String content)
    {
        final String checksumString = config.extractDigetsFromChecksumContent(content);
        if (checksumString.isEmpty())
        {
            throw new RuntimeException("Read invalid checksum file, missing checksum.");
        }

        return BitUtil.fromHex(checksumString);
    }

    protected String extractDataFileName(String content)
    {
        final String fileName = config.extractDataFileNameFromChecksumContent(content);
        if (fileName.isEmpty())
        {
            throw new RuntimeException("Read invalid checksum file, missing data file name.");
        }

        return fileName;
    }

    @Override
    public void validateAndClose() throws InvalidSnapshotException
    {
        final MessageDigest messageDigest = inputStream.getMessageDigest();

        FileUtil.closeSilently(inputStream);

        final byte[] digestOfBytesRead = messageDigest.digest();
        final boolean digestsEqual = Arrays.equals(digestOfBytesRead, checksum);

        if (!digestsEqual)
        {
            throw new InvalidSnapshotException("Read invalid snapshot, checksum doesn't match.");
        }
    }

    @Override
    public void delete()
    {
        FileUtil.closeSilently(inputStream);

        dataFile.delete();
        checksumFile.delete();
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

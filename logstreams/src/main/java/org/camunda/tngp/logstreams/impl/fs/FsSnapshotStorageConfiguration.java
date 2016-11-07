package org.camunda.tngp.logstreams.impl.fs;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FsSnapshotStorageConfiguration
{
    protected static final String CHECKSUM_ALGORITHM = "SHA1";

    protected static final String SNAPSHOT_FILE_NAME_TEMPLATE = "%s" + File.separatorChar + "%s-%d.snapshot";
    protected static final String SNAPSHOT_FILE_NAME_PATTERN = "%s-(\\d+).snapshot";

    protected static final String CHECKSUM_FILE_NAME_TEMPLATE = "%s" + File.separatorChar + "%s-%d." + CHECKSUM_ALGORITHM.toLowerCase();

    protected static final String CHECKSUM_CONTENT_SEPARATOR = ";";
    protected static final String CHECKSUM_CONTENT_TEMPLATE = "%s" + CHECKSUM_CONTENT_SEPARATOR + "%s";

    protected String rootPath;

    public void setRootPath(String rootPath)
    {
        this.rootPath = rootPath;
    }

    public String getRootPath()
    {
        return rootPath;
    }

    public String getChecksumAlgorithm()
    {
        return CHECKSUM_ALGORITHM;
    }

    public String snapshotFileName(String name, long logPosition)
    {
        return String.format(SNAPSHOT_FILE_NAME_TEMPLATE, rootPath, name, logPosition);
    }

    public String checksumFileName(String name, long logPosition)
    {
        return String.format(CHECKSUM_FILE_NAME_TEMPLATE, rootPath, name, logPosition);
    }

    public boolean matchesSnapshotFileNamePattern(File file, String name)
    {
        final String pattern = String.format(SNAPSHOT_FILE_NAME_PATTERN, name);
        return file.getName().matches(pattern);
    }

    public Long getPositionOfSnapshotFile(File file, String name)
    {
        final String fileName = file.getName();

        final String pattern = String.format(SNAPSHOT_FILE_NAME_PATTERN, name);
        final Matcher matcher = Pattern.compile(pattern).matcher(fileName);
        if (matcher.find())
        {
            final String position = matcher.group(1);
            return Long.parseLong(position);
        }
        else
        {
            throw new IllegalArgumentException("Cannot resolve position of snapshot file: " + fileName);
        }
    }

    public String checksumContent(String checksum, String dataFileName)
    {
        return String.format(CHECKSUM_CONTENT_TEMPLATE, checksum, dataFileName);
    }

    public String extractDigetsFromChecksumContent(String content)
    {
        final int indexOfSeparator = content.indexOf(CHECKSUM_CONTENT_SEPARATOR);
        if (indexOfSeparator < 0)
        {
            throw new RuntimeException("Read invalid checksum file, missing separator.");
        }

        return content.substring(0, indexOfSeparator);
    }

    public String extractDataFileNameFromChecksumContent(String content)
    {
        final int indexOfSeparator = content.indexOf(CHECKSUM_CONTENT_SEPARATOR);
        if (indexOfSeparator < 0)
        {
            throw new RuntimeException("Read invalid checksum file, missing separator.");
        }

        return content.substring(indexOfSeparator + 1);
    }

    public String getSnapshotFileNameTemplate()
    {
        return SNAPSHOT_FILE_NAME_TEMPLATE;
    }

    public String getChecksumFileNameTemplate()
    {
        return CHECKSUM_FILE_NAME_TEMPLATE;
    }

    public static String getChecksumContentTemplate()
    {
        return CHECKSUM_CONTENT_TEMPLATE;
    }

}

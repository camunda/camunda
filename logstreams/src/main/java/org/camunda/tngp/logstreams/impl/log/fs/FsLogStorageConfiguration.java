package org.camunda.tngp.logstreams.impl.log.fs;

import java.io.File;

public class FsLogStorageConfiguration
{
    protected static final String FRAGMENT_FILE_NAME_TEMPLATE = "%s" + File.separatorChar + "%02d.data";
    protected static final String FRAGMENT_FILE_NAME_PATTERN = "\\d{2}.data";

    protected final int segmentSize;
    protected final String path;
    protected final int initialSegmentId;
    protected final boolean deleteOnClose;

    public FsLogStorageConfiguration(int segmentSize, String path, int initialSegmentId, boolean deleteOnClose)
    {
        this.segmentSize = segmentSize;
        this.path = path;
        this.initialSegmentId = initialSegmentId;
        this.deleteOnClose = deleteOnClose;
    }

    public String getFragmentFileNameTemplate()
    {
        return FRAGMENT_FILE_NAME_TEMPLATE;
    }

    public int getSegmentSize()
    {
        return segmentSize;
    }

    public String getPath()
    {
        return path;
    }

    public String fileName(int segmentId)
    {
        return String.format(FRAGMENT_FILE_NAME_TEMPLATE, path, segmentId);
    }

    public boolean matchesFragmentFileNamePattern(File file)
    {
        return file.getName().matches(FRAGMENT_FILE_NAME_PATTERN);
    }

    public boolean isDeleteOnClose()
    {
        return deleteOnClose;
    }

    public int getInitialSegmentId()
    {
        return initialSegmentId;
    }
}

package org.camunda.tngp.logstreams.impl.fs;

import java.io.File;

public class FsStorageConfiguration
{
    protected final String fragmentFileNameTemplate = "%s" + File.separatorChar + "%02d.data";
    protected final int segmentSize;
    protected final String path;
    protected final int initialSegmentId;
    protected final boolean deleteOnClose;

    public FsStorageConfiguration(int segmentSize, String path, int initialSegmentId, boolean deleteOnClose)
    {
        this.segmentSize = segmentSize;
        this.path = path;
        this.initialSegmentId = initialSegmentId;
        this.deleteOnClose = deleteOnClose;
    }

    public String getFragmentFileNameTemplate()
    {
        return fragmentFileNameTemplate;
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
        return String.format(fragmentFileNameTemplate, path, segmentId);
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

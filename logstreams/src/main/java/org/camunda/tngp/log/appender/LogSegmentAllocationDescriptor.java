package org.camunda.tngp.log.appender;

import java.io.File;

public class LogSegmentAllocationDescriptor
{
    protected final String fragmentFileNameTemplate = "%s" + File.separatorChar + "%02d.data";
    protected final int segmentSize;
    protected final String path;
    protected final int initialSegmentId;

    public LogSegmentAllocationDescriptor(int segmentSize, String path, int initialSegmentId)
    {
        this.segmentSize = segmentSize;
        this.path = path;
        this.initialSegmentId = initialSegmentId;
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

}

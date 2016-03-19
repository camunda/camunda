package org.camunda.tngp.log.appender;

import java.io.File;

public class SegmentAllocationDescriptor
{
    protected final String fragmentFileNameTemplate = "%s" + File.separatorChar + "seg-%02d.data";
    protected int fragmentSize;
    protected String path;

    public SegmentAllocationDescriptor(int fragmentSize, String path)
    {
        this.fragmentSize = fragmentSize;
        this.path = path;
    }

    public String getFragmentFileNameTemplate()
    {
        return fragmentFileNameTemplate;
    }

    public int getSegmentSize()
    {
        return fragmentSize;
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

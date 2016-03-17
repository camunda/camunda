package org.camunda.tngp.log.appender;

import java.io.File;

public class LogAllocationDescriptor
{
    protected final String fragmentFileNameTemplate = "%s" + File.separatorChar + "frag-%02d.log";
    protected int fragmentSize;
    protected String path;

    public LogAllocationDescriptor(int fragmentSize, String path)
    {
        this.fragmentSize = fragmentSize;
        this.path = path;
    }

    public String getFragmentFileNameTemplate()
    {
        return fragmentFileNameTemplate;
    }
    public int getFragmentSize()
    {
        return fragmentSize;
    }
    public String getPath()
    {
        return path;
    }

}

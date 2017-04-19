package org.camunda.tngp.broker.logstreams.cfg;

public class LogStreamsCfg
{

    public static final int DEFAULT_LOG_ID = 0;
    public static final String DEFAULT_LOG_NAME = "default-log";

    public int defaultLogSegmentSize = 512;

    public String[] logDirectories = new String[0];
    public boolean useTempLogDirectory = false;

    public String indexDirectory = null;
    public boolean useTempIndexFile = false;

}

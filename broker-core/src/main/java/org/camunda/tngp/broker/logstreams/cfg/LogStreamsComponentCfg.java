package org.camunda.tngp.broker.logstreams.cfg;

import org.camunda.tngp.broker.system.ComponentConfiguration;

public class LogStreamsComponentCfg extends ComponentConfiguration
{
    public String[] logDirectories = new String[0];

    public int defaultLogSegmentSize = 512;

}
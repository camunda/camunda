package org.camunda.tngp.broker.logstreams.cfg;

import org.camunda.tngp.broker.system.DirectoryConfiguration;
import org.camunda.tngp.broker.system.GlobalConfiguration;
import org.camunda.tngp.util.FileUtil;

public class LogStreamsCfg extends DirectoryConfiguration
{
    public int defaultLogSegmentSize = 512;

    public String[] directories = null;

    @Override
    public void applyGlobalConfiguration(GlobalConfiguration globalConfig)
    {
        if (directories == null || directories.length == 0)
        {
            super.applyGlobalConfiguration(globalConfig);
            directories = new String[] { directory };
            return;
        }

        for (int i = 0; i < directories.length; i++)
        {
            directories[i] = FileUtil.getCanonicalPath(directories[i]);
        }
    }

    @Override
    protected String componentDirectoryName()
    {
        return "logs";
    }

}

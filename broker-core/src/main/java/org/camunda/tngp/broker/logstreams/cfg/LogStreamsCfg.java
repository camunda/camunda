package org.camunda.tngp.broker.logstreams.cfg;

import org.camunda.tngp.broker.system.ComponentConfiguration;
import org.camunda.tngp.broker.system.GlobalConfiguration;
import org.camunda.tngp.util.FileUtil;

public class LogStreamsCfg extends ComponentConfiguration
{

    public int defaultLogSegmentSize = 512;

    public String[] directories = null;


    @Override
    protected  void onApplyingGlobalConfiguration(GlobalConfiguration global)
    {




        this.directories = (String[]) new Rules("first")
                .setGlobalObj(global.directory)
                .setLocalObj(directories, "directories")
                .setRule((r) ->
                {
                    final String[] ret = new String[1];
                    ret[0] = r + "logs/";
                    return ret;
                }).execute();

        for (String each : this.directories)
        {
            each = FileUtil.getCanonicalDirectoryPath(each);
        }



    }

}

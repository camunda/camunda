package org.camunda.tngp.broker.logstreams.cfg;

import org.camunda.tngp.broker.system.ComponentConfiguration;
import org.camunda.tngp.broker.system.GlobalConfiguration;

public class LogStreamsCfg extends ComponentConfiguration
{

    public int defaultLogSegmentSize = 512;

    public String[] logDirectories = null;

    public String indexDirectory = null;

    @Override
    protected  void onApplyingGlobalConfiguration(GlobalConfiguration global)
    {


        this.indexDirectory = (String) new Rules("first")
             .setGlobalObj(global.globalDataDirectory)
             .setLocalObj(indexDirectory, "indexDirectory")
             .setRule((r) ->
             { return r + "index/"; }).execute();

        this.logDirectories = (String[]) new Rules("first")
                .setGlobalObj(global.globalDataDirectory)
                .setLocalObj(logDirectories, "logDirectories")
                .setRule((r) ->
                {
                    final String[] ret = new String[1];
                    ret[0] = r + "logs/";
                    return ret;
                }).execute();




    }

}

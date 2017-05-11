package org.camunda.tngp.broker.system.metrics.cfg;

import org.camunda.tngp.broker.system.ComponentConfiguration;
import org.camunda.tngp.broker.system.GlobalConfiguration;
import org.camunda.tngp.util.FileUtil;

public class MetricsCfg extends ComponentConfiguration
{
    public String directory = "/tmp/metrics/";

    @Override
    protected  void onApplyingGlobalConfiguration(GlobalConfiguration global)
    {

        this.directory = (String) new Rules("first")
             .setGlobalObj(global.directory)
             .setLocalObj(directory, "directory")
             .setRule((r) ->
             { return r + "metrics/"; }).execute();

        this.directory = FileUtil.getCanonicalDirectoryPath(this.directory);

    }
}

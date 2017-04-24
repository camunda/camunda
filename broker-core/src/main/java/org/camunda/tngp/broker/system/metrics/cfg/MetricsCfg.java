package org.camunda.tngp.broker.system.metrics.cfg;

import org.camunda.tngp.broker.system.ComponentConfiguration;
import org.camunda.tngp.broker.system.GlobalConfiguration;

public class MetricsCfg extends ComponentConfiguration
{
    public String countersFileName;

    @Override
    protected  void onApplyingGlobalConfiguration(GlobalConfiguration global)
    {

        this.countersFileName = (String) new Rules("first")
             .setGlobalObj(global.globalDataDirectory)
             .setLocalObj(countersFileName, "countersFileName")
             .setRule((r) ->
             { return r + "metrics/tngp.metrics"; }).execute();


    }
}

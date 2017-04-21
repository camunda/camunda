package org.camunda.tngp.broker.system.metrics.cfg;

import org.camunda.tngp.broker.system.ComponentConfiguration;
import org.camunda.tngp.broker.system.GlobalConfiguration;

public class MetricsCfg extends ComponentConfiguration
{
    public String countersFileName;
    public boolean useTempCountersFile = false;

    @Override
    protected  void onApplyingGlobalConfiguration(GlobalConfiguration global)
    {

        this.countersFileName = (String) new Rules("first")
             .setGlobalObj(global.globalDataDirectory)
             .setLocalObj(countersFileName, "countersFileName")
             .setRule((r) ->
             { return r + "metrics/tngp.metrics"; }).execute();

        this.useTempCountersFile = (boolean) new Rules("second")
                .setGlobalObj(global.globalUseTemp)
                .setLocalObj(useTempCountersFile, "useTempCountersFile")
                .setRule((r) ->
                { return r; }).execute();

    }
}

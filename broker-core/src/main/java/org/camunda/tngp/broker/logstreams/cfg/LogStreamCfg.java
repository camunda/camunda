package org.camunda.tngp.broker.logstreams.cfg;

import org.camunda.tngp.broker.system.ComponentConfiguration;
import org.camunda.tngp.broker.system.GlobalConfiguration;

public class LogStreamCfg extends ComponentConfiguration
{
    public String name = null;

    public int id = -1;

    public boolean useTempLogDirectory = false;

    public String logDirectory = null;

    public int logSegmentSize = -1;


    @Override
    protected  void onApplyingGlobalConfiguration(GlobalConfiguration global)
    {

        this.logDirectory = (String) new Rules("first")
             .setGlobalObj(global.globalDataDirectory)
             .setLocalObj(logDirectory, "logDirectory")
             .setRule((r) ->
             { return r + "logs/" + id + '-' + name + "/"; }).execute();

        this.useTempLogDirectory = (boolean) new Rules("second")
                .setGlobalObj(global.globalUseTemp)
                .setLocalObj(useTempLogDirectory, "useTempLogDirectory")
                .setRule((r) ->
                { return r; }).execute();

    }
}

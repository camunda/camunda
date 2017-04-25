package org.camunda.tngp.broker.logstreams.cfg;

import org.camunda.tngp.broker.system.ComponentConfiguration;
import org.camunda.tngp.broker.system.GlobalConfiguration;

public class LogStreamsCfg extends ComponentConfiguration
{

    public int defaultLogSegmentSize = 512;

    public String[] logDirectories = new String[0];
    public boolean useTempLogDirectory = false;

    public String indexDirectory = null;
    public boolean useTempIndexFile = false;

    @Override
    protected  void onApplyingGlobalConfiguration(GlobalConfiguration global)
    {

        this.indexDirectory = (String) new Rules("first")
             .setGlobalObj(global.globalDataDirectory)
             .setLocalObj(indexDirectory, "indexDirectory")
             .setRule((r) ->
             { return r + "logs/"; }).execute();

        this.useTempLogDirectory = (boolean) new Rules("second")
                .setGlobalObj(global.globalUseTemp)
                .setLocalObj(useTempLogDirectory, "useTempLogDirectory")
                .setRule((r) ->
                { return r; }).execute();

        this.useTempIndexFile = (boolean) new Rules("second")
                .setGlobalObj(global.globalUseTemp)
                .setLocalObj(useTempIndexFile, "useTempIndexFile")
                .setRule((r) ->
                { return r; }).execute();

    }

}

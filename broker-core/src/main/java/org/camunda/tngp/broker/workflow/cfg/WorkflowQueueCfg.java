package org.camunda.tngp.broker.workflow.cfg;

import org.camunda.tngp.broker.system.ComponentConfiguration;
import org.camunda.tngp.broker.system.GlobalConfiguration;

public class WorkflowQueueCfg extends ComponentConfiguration
{
    public String logName;

    public String indexDirectory;
    public boolean useTempIndexFile = false;

    @Override
    protected  void onApplyingGlobalConfiguration(GlobalConfiguration global)
    {

        this.indexDirectory = (String) new Rules("first")
             .setGlobalObj(global.globalDataDirectory)
             .setLocalObj(indexDirectory, "indexDirectory")
             .setRule((r) ->
             { return r + "workflow-queue-" + logName + "/"; }).execute();

        this.useTempIndexFile = (boolean) new Rules("second")
                .setGlobalObj(global.globalUseTemp)
                .setLocalObj(useTempIndexFile, "useTempIndexFile")
                .setRule((r) ->
                { return r; }).execute();

    }
}


package org.camunda.tngp.broker.clustering.management.config;

import org.camunda.tngp.broker.system.ComponentConfiguration;
import org.camunda.tngp.broker.system.GlobalConfiguration;

public class ClusterManagementConfig extends ComponentConfiguration
{
    public String metaDirectory;

    @Override
    protected  void onApplyingGlobalConfiguration(GlobalConfiguration global)
    {

        this.metaDirectory = (String) new Rules("first")
             .setGlobalObj(global.globalDataDirectory)
             .setLocalObj(metaDirectory, "metaDirectory")
             .setRule((r) ->
             { return r + "meta/"; }).execute();

    }
}

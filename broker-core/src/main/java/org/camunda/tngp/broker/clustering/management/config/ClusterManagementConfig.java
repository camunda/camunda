package org.camunda.tngp.broker.clustering.management.config;

import org.camunda.tngp.broker.system.ComponentConfiguration;
import org.camunda.tngp.broker.system.GlobalConfiguration;

public class ClusterManagementConfig extends ComponentConfiguration
{
    public String directory;

    @Override
    protected  void onApplyingGlobalConfiguration(GlobalConfiguration global)
    {

        this.directory = (String) new Rules("first")
             .setGlobalObj(global.directory)
             .setLocalObj(directory, "directory")
             .setRule((r) ->
             { return r + "meta/"; }).execute();

    }
}

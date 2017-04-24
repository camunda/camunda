package org.camunda.tngp.broker.event.processor;

import org.camunda.tngp.broker.system.ComponentConfiguration;
import org.camunda.tngp.broker.system.GlobalConfiguration;


public class SubscriptionCfg extends ComponentConfiguration
{

    public String snapshotDirectory;

    @Override
    protected  void onApplyingGlobalConfiguration(GlobalConfiguration global)
    {

        this.snapshotDirectory = (String) new Rules("first")
             .setGlobalObj(global.globalDataDirectory)
             .setLocalObj(snapshotDirectory, "snapshotDirectory")
             .setRule((r) ->
             { return r + "subscription/"; }).execute();


    }

}

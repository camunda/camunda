package org.camunda.tngp.broker.event.processor;

import org.camunda.tngp.broker.system.ComponentConfiguration;
import org.camunda.tngp.broker.system.GlobalConfiguration;
import org.camunda.tngp.util.FileUtil;


public class SubscriptionCfg extends ComponentConfiguration
{

    public String directory;

    @Override
    protected void onApplyingGlobalConfiguration(GlobalConfiguration global)
    {

        this.directory = (String) new Rules("first")
            .setGlobalObj(global.directory)
            .setLocalObj(directory, "directory")
            .setRule((r) ->
            {
                return r + "subscription/";
            }).execute();

        this.directory = FileUtil.getCanonicalDirectoryPath(this.directory);


    }

}

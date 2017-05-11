package org.camunda.tngp.broker.logstreams.cfg;

import org.camunda.tngp.broker.system.ComponentConfiguration;
import org.camunda.tngp.broker.system.GlobalConfiguration;

public class StreamProcessorCfg extends ComponentConfiguration
{


    public String directory = "/tmp/index/";

    @Override
    protected  void onApplyingGlobalConfiguration(GlobalConfiguration global)
    {


        this.directory = (String) new Rules("first")
             .setGlobalObj(global.directory)
             .setLocalObj(directory, "directory")
             .setRule((r) ->
             { return r + "index/"; }).execute();

    }

}

package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.broker.wf.cfg.WfRuntimeCfg;

public interface WfRuntimeManager extends ResourceContextProvider<WfRuntimeContext>
{
    void createWorkflowInstanceQueue(WfRuntimeCfg cfg);
}

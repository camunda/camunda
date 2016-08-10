package org.camunda.tngp.broker.wf.runtime;

import java.util.List;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.broker.wf.cfg.WfRuntimeCfg;

public interface WfRuntimeManager extends ResourceContextProvider<WfRuntimeContext>
{
    void createWorkflowInstanceQueue(WfRuntimeCfg cfg);

    List<LogConsumer> getInputLogConsumers();

    void registerInputLogConsumer(LogConsumer logConsumer);
}

package org.camunda.tngp.broker.wf.repository;

import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.broker.wf.cfg.WfRepositoryCfg;

public interface WfRepositoryManager extends ResourceContextProvider<WfRepositoryContext>
{
    public void createRepository(WfRepositoryCfg cfg);
}

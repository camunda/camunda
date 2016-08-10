package org.camunda.tngp.broker.wf.repository;

import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.ServiceName;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class WfRepositoryServiceNames
{
    public static final ServiceName<WfRepositoryManager> WF_REPOSITORY_MANAGER_NAME = ServiceName.newServiceName("wf.repository.manager", WfRepositoryManager.class);

    public static ServiceName<IdGenerator> wfDefinitionIdGeneratorServiceName(String repositoryName)
    {
        return ServiceName.newServiceName(String.format("wf.repository.%s.definition.id-generator", repositoryName), IdGenerator.class);
    }

    public static ServiceName<WfRepositoryContext> wfRepositoryContextName(String contextName)
    {
        return ServiceName.newServiceName(String.format("wf.repository.%s.ctx", contextName), WfRepositoryContext.class);
    }

}

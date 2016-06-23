package org.camunda.tngp.broker.wf.repository;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.ServiceName;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class WfRepositoryServiceNames
{
    public static final ServiceName<WfRepositoryManager> WF_REPOSITORY_MANAGER_NAME = ServiceName.newServiceName("wf.repository.manager", WfRepositoryManager.class);

    public static ServiceName<IdGenerator> wfTypeIdGeneratorServiceName(String repositoryName)
    {
        return ServiceName.newServiceName(String.format("wf.repository.%s.type.id-generator", repositoryName), IdGenerator.class);
    }

    public static ServiceName<HashIndexManager<Long2LongHashIndex>> wfTypeIdIndexServiceName(String contextName)
    {
        return (ServiceName) ServiceName.newServiceName(String.format("wf.repository.%s.type.id-index", contextName), HashIndexManager.class);
    }

    public static ServiceName<HashIndexManager<Bytes2LongHashIndex>> wfTypeKeyIndexServiceName(String contextName)
    {
        return (ServiceName) ServiceName.newServiceName(String.format("wf.repository.%s.type.key-index", contextName), HashIndexManager.class);
    }

    public static ServiceName<WfTypeCacheService> wfTypeCacheServiceName(String contextName)
    {
        return ServiceName.newServiceName(String.format("wf.repository.%s.type.cache", contextName), WfTypeCacheService.class);
    }

    public static ServiceName<WfRepositoryContext> wfRepositoryContextName(String contextName)
    {
        return ServiceName.newServiceName(String.format("wf.repository.%s.ctx", contextName), WfRepositoryContext.class);
    }

}

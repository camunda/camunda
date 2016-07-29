package org.camunda.tngp.broker.wf.repository;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.repository.idx.WfDefinitionIndexWriter;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class WfRepositoryContextService implements Service<WfRepositoryContext>
{
    protected final Injector<Log> wfDefinitionLogInjector = new Injector<>();
    protected final Injector<IdGenerator> wfDefinitionIdGeneratorInjector = new Injector<>();
    protected final Injector<HashIndexManager<Bytes2LongHashIndex>> wfDefinitionKeyIndexInjector = new Injector<>();
    protected final Injector<HashIndexManager<Long2LongHashIndex>> wfDefinitionIdIndexInjector = new Injector<>();
    protected final Injector<WfDefinitionCacheService> wfDefinitionCacheServiceInjector = new Injector<>();

    protected final WfRepositoryContext context;

    public WfRepositoryContextService(int id, String name)
    {
        this.context = new WfRepositoryContext(id, name);
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        context.setWfDefinitionLog(wfDefinitionLogInjector.getValue());
        context.setWfDefinitionIdGenerator(wfDefinitionIdGeneratorInjector.getValue());
        context.setWfDefinitionKeyIndex(wfDefinitionKeyIndexInjector.getValue());
        context.setWfDefinitionIdIndex(wfDefinitionIdIndexInjector.getValue());
        context.setWfDefinitionCacheService(wfDefinitionCacheServiceInjector.getValue());
        context.setWfDefinitionIndexWriter(new WfDefinitionIndexWriter(context));
    }

    @Override
    public void stop()
    {
        final WfDefinitionIndexWriter wfDefinitionIndexWriter = context.getWfDefinitionIndexWriter();
        wfDefinitionIndexWriter.update();
        wfDefinitionIndexWriter.writeCheckpoints();
    }

    @Override
    public WfRepositoryContext get()
    {
        return context;
    }

    public Injector<Log> getWfDefinitionLogInjector()
    {
        return wfDefinitionLogInjector;
    }

    public Injector<IdGenerator> getWfDefinitionIdGeneratorInjector()
    {
        return wfDefinitionIdGeneratorInjector;
    }

    public Injector<HashIndexManager<Bytes2LongHashIndex>> getWfDefinitionKeyIndexInjector()
    {
        return wfDefinitionKeyIndexInjector;
    }

    public Injector<HashIndexManager<Long2LongHashIndex>> getWfDefinitionIdIndexInjector()
    {
        return wfDefinitionIdIndexInjector;
    }

    public Injector<WfDefinitionCacheService> getWfDefinitionCacheServiceInjector()
    {
        return wfDefinitionCacheServiceInjector;
    }
}

package org.camunda.tngp.broker.wf.repository;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.repository.idx.WfTypeIndexWriter;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class WfRepositoryContextService implements Service<WfRepositoryContext>
{
    protected final Injector<Log> wfTypeLogInjector = new Injector<>();
    protected final Injector<IdGenerator> wfTypeIdGeneratorInjector = new Injector<>();
    protected final Injector<HashIndexManager<Bytes2LongHashIndex>> wfTypeKeyIndexInjector = new Injector<>();
    protected final Injector<HashIndexManager<Long2LongHashIndex>> wfTypeIdIndexInjector = new Injector<>();
    protected final Injector<WfTypeCacheService> wfTypeCacheServiceInjector = new Injector<>();

    protected final WfRepositoryContext context;

    public WfRepositoryContextService(int id, String name)
    {
        this.context = new WfRepositoryContext(id, name);
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        context.setWfTypeLog(wfTypeLogInjector.getValue());
        context.setWfTypeIdGenerator(wfTypeIdGeneratorInjector.getValue());
        context.setWfTypeKeyIndex(wfTypeKeyIndexInjector.getValue());
        context.setWfTypeIdIndex(wfTypeIdIndexInjector.getValue());
        context.setWfTypeCacheService(wfTypeCacheServiceInjector.getValue());
        context.setWfTypeIndexWriter(new WfTypeIndexWriter(context));
    }

    @Override
    public void stop()
    {
        final WfTypeIndexWriter wfTypeIndexWriter = context.getWfTypeIndexWriter();
        wfTypeIndexWriter.update();
        wfTypeIndexWriter.writeCheckpoints();
    }

    @Override
    public WfRepositoryContext get()
    {
        return context;
    }

    public Injector<Log> getWfTypeLogInjector()
    {
        return wfTypeLogInjector;
    }

    public Injector<IdGenerator> getWfTypeIdGeneratorInjector()
    {
        return wfTypeIdGeneratorInjector;
    }

    public Injector<HashIndexManager<Bytes2LongHashIndex>> getWfTypeKeyIndexInjector()
    {
        return wfTypeKeyIndexInjector;
    }

    public Injector<HashIndexManager<Long2LongHashIndex>> getWfTypeIdIndexInjector()
    {
        return wfTypeIdIndexInjector;
    }

    public Injector<WfTypeCacheService> getWfTypeCacheServiceInjector()
    {
        return wfTypeCacheServiceInjector;
    }
}

package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class WfDefinitionCacheService implements Service<WfDefinitionCache>
{
    protected final Injector<Log> wfDefinitionLogInjector = new Injector<>();
    protected final Injector<HashIndexManager<Long2LongHashIndex>> wfDefinitionIdIndexInjector = new Injector<>();
    protected final Injector<HashIndexManager<Bytes2LongHashIndex>> wfDefinitionKeyIndexInjector = new Injector<>();

    protected final int numSets;
    protected final int setSize;

    protected WfDefinitionCache wfTypeCache;

    public WfDefinitionCacheService(int numSets, int setSize)
    {
        this.numSets = numSets;
        this.setSize = setSize;
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        wfTypeCache = new WfDefinitionCache(
                numSets,
                setSize,
                wfDefinitionLogInjector.getValue(),
                wfDefinitionIdIndexInjector.getValue().getIndex(),
                wfDefinitionKeyIndexInjector.getValue().getIndex());
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        // nothing to do
    }

    @Override
    public WfDefinitionCache get()
    {
        return wfTypeCache;
    }

    public Injector<HashIndexManager<Long2LongHashIndex>> getWfDefinitionIdIndexInjector()
    {
        return wfDefinitionIdIndexInjector;
    }

    public Injector<HashIndexManager<Bytes2LongHashIndex>> getWfDefinitionKeyIndexInjector()
    {
        return wfDefinitionKeyIndexInjector;
    }

    public Injector<Log> getWfDefinitionLogInjector()
    {
        return wfDefinitionLogInjector;
    }

}

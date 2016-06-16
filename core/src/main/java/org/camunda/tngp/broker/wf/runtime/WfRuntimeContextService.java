package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.broker.wf.repository.WfTypeCacheService;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class WfRuntimeContextService implements Service<WfRuntimeContext>
{
    protected final Injector<WfTypeCacheService> wfTypeChacheInjector = new Injector<>();
    protected final Injector<IdGenerator> idGeneratorInjector = new Injector<>();
    protected final Injector<Log> logInjector = new Injector<>();

    protected final WfRuntimeContext wfRuntimeContext;

    public WfRuntimeContextService(int id, String name)
    {
        wfRuntimeContext = new WfRuntimeContext(id, name);
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        wfRuntimeContext.setWfTypeCacheService(wfTypeChacheInjector.getValue());
        wfRuntimeContext.setIdGenerator(idGeneratorInjector.getValue());
        wfRuntimeContext.setLog(logInjector.getValue());
    }

    @Override
    public void stop()
    {
        // nothing to do
    }

    @Override
    public WfRuntimeContext get()
    {
        return wfRuntimeContext;
    }

    public Injector<WfTypeCacheService> getWfTypeChacheInjector()
    {
        return wfTypeChacheInjector;
    }

    public Injector<IdGenerator> getIdGeneratorInjector()
    {
        return idGeneratorInjector;
    }

    public Injector<Log> getLogInjector()
    {
        return logInjector;
    }

}

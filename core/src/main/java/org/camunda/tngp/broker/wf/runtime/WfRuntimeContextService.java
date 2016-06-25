package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.broker.wf.repository.WfTypeCacheService;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class WfRuntimeContextService implements Service<WfRuntimeContext>
{
    // TODO: move somewhere else?
    protected static final int READ_BUFFER_SIZE = 1024 * 1024;

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

        final Log log = logInjector.getValue();
        final LogReader logReader = new LogReader(log, READ_BUFFER_SIZE);
        final LogWriter logWriter = new LogWriter(log);

        wfRuntimeContext.setLogReader(logReader);
        wfRuntimeContext.setLogWriter(logWriter);

        // TODO: is it good to instantiate the handler here?
        wfRuntimeContext.setBpmnEventHandler(new BpmnEventHandler(wfTypeChacheInjector.getValue(), logReader, logWriter));
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

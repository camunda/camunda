package org.camunda.tngp.broker.wf.repository;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.wf.repository.log.handler.WfDefinitionRequestHandler;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReaderImpl;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;

public class WfRepositoryContextService implements Service<WfRepositoryContext>
{
    protected final Injector<Log> wfDefinitionLogInjector = new Injector<>();
    protected final Injector<IdGenerator> wfDefinitionIdGeneratorInjector = new Injector<>();

    protected Injector<DeferredResponsePool> responsePoolInjector = new Injector<>();

    protected final WfRepositoryContext context;

    public WfRepositoryContextService(int id, String name)
    {
        this.context = new WfRepositoryContext(id, name);
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        final Log log = wfDefinitionLogInjector.getValue();
        context.setLog(log);

        final IdGenerator idGenerator = wfDefinitionIdGeneratorInjector.getValue();

        final LogWriter logWriter = new LogWriter(log);
        context.setLogWriter(logWriter);

        final Templates templates = Templates.wfRepositoryLogTemplates();
        final LogConsumer logConsumer = new LogConsumer(new LogReaderImpl(log), responsePoolInjector.getValue(), templates);

        logConsumer.addHandler(Templates.WF_DEFINITION_REQUEST,
                new WfDefinitionRequestHandler(new LogReaderImpl(log), logWriter, idGenerator));

        context.setLogConsumer(logConsumer);

    }

    @Override
    public void stop()
    {
        context.getLogConsumer().writeSafepoints();
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

    public Injector<DeferredResponsePool> getResponsePoolServiceInjector()
    {
        return responsePoolInjector;
    }
}

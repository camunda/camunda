package org.camunda.tngp.broker.wf;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeManager;
import org.camunda.tngp.broker.wf.runtime.log.handler.InputWorkflowDefinitionHandler;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReaderImpl;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class WfDefinitionLogProcessorService implements Service<LogConsumer>
{
    protected LogConsumer logConsumer;

    protected Injector<WfRuntimeManager> wfRuntimeManagerInjector = new Injector<>();
    protected Injector<WfRepositoryContext> wfRepositoryContextInjector = new Injector<>();

    @Override
    public void start(ServiceContext serviceContext)
    {
        final WfRuntimeManager wfRuntimeManager = wfRuntimeManagerInjector.getValue();

        final WfRepositoryContext wfRepositoryContext = wfRepositoryContextInjector.getValue();
        final Log inputLog = wfRepositoryContext.getLog();

        final Templates wfRepositoryLogTemplates = Templates.wfRepositoryLogTemplates();
        logConsumer = new LogConsumer(new LogReaderImpl(inputLog), wfRepositoryLogTemplates);

        logConsumer.addHandler(Templates.WF_DEFINITION, new InputWorkflowDefinitionHandler(wfRuntimeManager));

        wfRuntimeManager.registerInputLogConsumer(logConsumer);

        // TODO: restore log consumer position here

    }

    @Override
    public void stop()
    {
        // TODO Auto-generated method stub

    }

    @Override
    public LogConsumer get()
    {
        return logConsumer;
    }

    public Injector<WfRuntimeManager> getWfRuntimeManagerInjector()
    {
        return wfRuntimeManagerInjector;
    }

    public Injector<WfRepositoryContext> getWfRepositoryContextInjector()
    {
        return wfRepositoryContextInjector;
    }

}

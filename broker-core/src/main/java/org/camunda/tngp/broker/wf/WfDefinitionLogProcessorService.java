package org.camunda.tngp.broker.wf;

import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.log.LogWritersImpl;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeManager;
import org.camunda.tngp.broker.wf.runtime.log.handler.InputWorkflowDefinitionHandler;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReader;
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
        logConsumer = new LogConsumer(
                inputLog.getId(),
                new LogReaderImpl(inputLog),
                wfRepositoryLogTemplates,
                new LogWritersImpl(null, wfRuntimeManager));

        logConsumer.addHandler(Templates.WF_DEFINITION, new InputWorkflowDefinitionHandler());

        final List<LogReader> logReaders = new ArrayList<>();
        for (WfRuntimeContext resourceContext : wfRuntimeManager.getContexts())
        {
            logReaders.add(new LogReaderImpl(resourceContext.getLog()));
        }

        logConsumer.recover(logReaders);
        logConsumer.fastForwardUntil(inputLog.getLastPosition());

        wfRuntimeManager.registerInputLogConsumer(logConsumer);
    }

    @Override
    public void stop()
    {
        logConsumer.writeSavepoints();
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

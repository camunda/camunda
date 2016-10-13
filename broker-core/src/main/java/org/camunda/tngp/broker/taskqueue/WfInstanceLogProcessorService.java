package org.camunda.tngp.broker.taskqueue;

import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.log.LogWritersImpl;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.taskqueue.log.handler.InputActivityInstanceHandler;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.log.BufferedLogReader;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class WfInstanceLogProcessorService implements Service<LogConsumer>
{
    protected LogConsumer logConsumer;


    protected final Injector<TaskQueueManager> taskQueueManagerInjector = new Injector<>();
    protected final Injector<WfRuntimeContext> wfRuntimeContextInjector = new Injector<>();

    @Override
    public void start(ServiceContext serviceContext)
    {
        final WfRuntimeContext wfRuntimeContext = wfRuntimeContextInjector.getValue();
        final TaskQueueManager taskQueueManager = taskQueueManagerInjector.getValue();

        final Templates wfRuntimeTemplates = Templates.wfRuntimeLogTemplates();
        final Log log = wfRuntimeContext.getLog();

        logConsumer = new LogConsumer(
                log.getId(),
                new BufferedLogReader(log),
                wfRuntimeTemplates,
                new LogWritersImpl(null, taskQueueManager));

        logConsumer.addHandler(Templates.ACTIVITY_EVENT, new InputActivityInstanceHandler(taskQueueManager));

        final List<LogReader> logReaders = new ArrayList<>();
        for (TaskQueueContext resourceContext : taskQueueManager.getContexts())
        {
            logReaders.add(new BufferedLogReader(resourceContext.getLog()));
        }

        logConsumer.recover(logReaders);
        logConsumer.fastForwardToLastEvent();

        taskQueueManagerInjector.getValue().registerInputLogConsumer(logConsumer);
    }

    @Override
    public void stop()
    {
        // TODO: deregister consumer here
        logConsumer.writeSavepoints();
    }

    @Override
    public LogConsumer get()
    {
        return logConsumer;
    }

    public Injector<TaskQueueManager> getTaskQueueManager()
    {
        return taskQueueManagerInjector;
    }

    public Injector<WfRuntimeContext> getWfRuntimeContext()
    {
        return wfRuntimeContextInjector;
    }
}

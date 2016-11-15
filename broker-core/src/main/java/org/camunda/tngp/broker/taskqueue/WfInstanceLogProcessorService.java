package org.camunda.tngp.broker.taskqueue;

import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.log.LogWritersImpl;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.taskqueue.log.handler.InputActivityInstanceHandler;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.logstreams.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.logstreams.LogStreamReader;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class WfInstanceLogProcessorService implements Service<LogConsumer>
{
    protected LogConsumer logConsumer;


    protected final Injector<TaskQueueManager> taskQueueManagerInjector = new Injector<>();
    protected final Injector<WfRuntimeContext> wfRuntimeContextInjector = new Injector<>();

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        final WfRuntimeContext wfRuntimeContext = wfRuntimeContextInjector.getValue();
        final TaskQueueManager taskQueueManager = taskQueueManagerInjector.getValue();

        serviceContext.run(() ->
        {
            final Templates wfRuntimeTemplates = Templates.wfRuntimeLogTemplates();
            final LogStream log = wfRuntimeContext.getLog();

            logConsumer = new LogConsumer(
                    log.getId(),
                    new BufferedLogStreamReader(log),
                    wfRuntimeTemplates,
                    new LogWritersImpl(null, taskQueueManager));

            logConsumer.addHandler(Templates.ACTIVITY_EVENT, new InputActivityInstanceHandler(taskQueueManager));

            final List<LogStreamReader> logReaders = new ArrayList<>();
            for (TaskQueueContext resourceContext : taskQueueManager.getContexts())
            {
                logReaders.add(new BufferedLogStreamReader(resourceContext.getLog()));
            }

            logConsumer.recover(logReaders);
            logConsumer.fastForwardToLastEvent();

            taskQueueManager.registerInputLogConsumer(logConsumer);
        });
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.run(() ->
        {
            // TODO: deregister consumer here
            logConsumer.writeSavepoints();
        });
    }

    @Override
    public LogConsumer get()
    {
        return logConsumer;
    }

    public Injector<TaskQueueManager> getTaskQueueManagerInjector()
    {
        return taskQueueManagerInjector;
    }

    public Injector<WfRuntimeContext> getWfRuntimeContextInjector()
    {
        return wfRuntimeContextInjector;
    }
}

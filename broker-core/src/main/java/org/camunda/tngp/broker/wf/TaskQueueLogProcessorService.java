package org.camunda.tngp.broker.wf;

import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.log.LogWritersImpl;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeManager;
import org.camunda.tngp.broker.wf.runtime.log.handler.InputTaskHandler;
import org.camunda.tngp.logstreams.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.logstreams.LogStreamReader;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class TaskQueueLogProcessorService implements Service<LogConsumer>
{

    protected LogConsumer logConsumer;

    protected Injector<WfRuntimeManager> wfRuntimeManagerInjector = new Injector<>();
    protected Injector<TaskQueueContext> taskQueueContextInjector = new Injector<>();

    public TaskQueueLogProcessorService()
    {
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        serviceContext.run(() ->
        {
            final WfRuntimeManager wfRuntimeManager = wfRuntimeManagerInjector.getValue();

            final TaskQueueContext taskQueueContext = taskQueueContextInjector.getValue();
            final LogStream inputLog = taskQueueContext.getLog();

            final Templates taskQueueLogTemplates = Templates.taskQueueLogTemplates();
            logConsumer = new LogConsumer(
                    inputLog.getId(),
                    new BufferedLogStreamReader(inputLog),
                    taskQueueLogTemplates,
                    new LogWritersImpl(null, wfRuntimeManager));

            logConsumer.addHandler(Templates.TASK_INSTANCE, new InputTaskHandler());

            final List<LogStreamReader> logReaders = new ArrayList<>();
            for (WfRuntimeContext resourceContext : wfRuntimeManager.getContexts())
            {
                logReaders.add(new BufferedLogStreamReader(resourceContext.getLog()));
            }

            logConsumer.recover(logReaders);
            logConsumer.fastForwardToLastEvent();

            wfRuntimeManager.registerInputLogConsumer(logConsumer);
        });
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.run(() ->
        {
            logConsumer.writeSavepoints();
        });
    }

    public Injector<WfRuntimeManager> getWfRuntimeManagerInjector()
    {
        return wfRuntimeManagerInjector;
    }

    public Injector<TaskQueueContext> getTaskQueueContext()
    {
        return taskQueueContextInjector;
    }


    @Override
    public LogConsumer get()
    {
        return logConsumer;
    }

}

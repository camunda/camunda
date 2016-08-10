package org.camunda.tngp.broker.wf;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeManager;
import org.camunda.tngp.broker.wf.runtime.log.handler.InputTaskHandler;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReaderImpl;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class TaskQueueLogProcessorService implements Service<LogConsumer>
{

    protected LogConsumer logConsumer;

    protected Injector<WfRuntimeManager> wfRuntimeManagerInjector = new Injector<>();
    protected Injector<TaskQueueContext> taskQueueContextInjector = new Injector<>();

    public TaskQueueLogProcessorService()
    {
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        final WfRuntimeManager wfRuntimeManager = wfRuntimeManagerInjector.getValue();

        final TaskQueueContext taskQueueContext = taskQueueContextInjector.getValue();
        final Log inputLog = taskQueueContext.getLog();

        final Templates taskQueueLogTemplates = Templates.taskQueueLogTemplates();
        logConsumer = new LogConsumer(new LogReaderImpl(inputLog), taskQueueLogTemplates);

        logConsumer.addHandler(Templates.TASK_INSTANCE, new InputTaskHandler(wfRuntimeManager));

        wfRuntimeManager.registerInputLogConsumer(logConsumer);

        // TODO: restore log consumer position here
    }

    @Override
    public void stop()
    {
        // TODO write index checkpoints here
    }

    public Injector<WfRuntimeManager> getWfRuntimeManager()
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

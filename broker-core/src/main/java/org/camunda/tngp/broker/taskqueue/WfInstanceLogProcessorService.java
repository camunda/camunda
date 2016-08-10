package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.taskqueue.log.handler.InputActivityInstanceHandler;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.log.LogReaderImpl;
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
        logConsumer = new LogConsumer(new LogReaderImpl(wfRuntimeContext.getLog()), wfRuntimeTemplates);

        logConsumer.addHandler(Templates.ACTIVITY_EVENT, new InputActivityInstanceHandler(taskQueueManager));

        taskQueueManagerInjector.getValue().registerInputLogConsumer(logConsumer);

        // TODO: restore position
    }

    @Override
    public void stop()
    {
        // TODO: write checkpoints
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

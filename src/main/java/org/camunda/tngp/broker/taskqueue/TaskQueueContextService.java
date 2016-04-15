package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.servicecontainer.Injector;
import org.camunda.tngp.broker.servicecontainer.Service;
import org.camunda.tngp.broker.servicecontainer.ServiceContext;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.index.LogIndex;

public class TaskQueueContextService implements Service<TaskQueueContext>
{
    protected final Injector<Log> logInjector = new Injector<>();
    protected final Injector<IdGenerator> taskInstanceIdGeneratorInjector = new Injector<>();
    protected final Injector<LogIndex> taskInstanceIdIndexInjector = new Injector<>();

    protected final TaskQueueContext taskQueueContext;

    public TaskQueueContextService(String taskQueueName, int taskQueueId)
    {
        taskQueueContext = new TaskQueueContext(taskQueueName, taskQueueId);
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        taskQueueContext.setLog(logInjector.getValue());
        taskQueueContext.setTaskInstanceIdGenerator(taskInstanceIdGeneratorInjector.getValue());
        taskQueueContext.setTaskInstanceIndex(taskInstanceIdIndexInjector.getValue());
    }

    @Override
    public void stop()
    {
        // nothing to do
    }

    @Override
    public TaskQueueContext get()
    {
        return taskQueueContext;
    }

    public Injector<Log> getLogInjector()
    {
        return logInjector;
    }

    public Injector<IdGenerator> getTaskInstanceIdGeneratorInjector()
    {
        return taskInstanceIdGeneratorInjector;
    }

    public Injector<LogIndex> getTaskInstanceIdIndexInjector()
    {
        return taskInstanceIdIndexInjector;
    }

}

package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.servicecontainer.Injector;
import org.camunda.tngp.broker.servicecontainer.Service;
import org.camunda.tngp.broker.servicecontainer.ServiceContext;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class TaskQueueContextService implements Service<TaskQueueContext>
{
    protected final Injector<Log> logInjector = new Injector<>();
    protected final Injector<IdGenerator> taskInstanceIdGeneratorInjector = new Injector<>();
    protected final Injector<HashIndexManager<Long2LongHashIndex>> lockedTasksIndexServiceInjector = new Injector<>();
    protected final Injector<HashIndexManager<Bytes2LongHashIndex>> taskTypeIndexServiceInjector = new Injector<>();

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
        taskQueueContext.setLockedTaskInstanceIndex(lockedTasksIndexServiceInjector.getValue());
        taskQueueContext.setTaskTypePositionIndex(taskTypeIndexServiceInjector.getValue());
        taskQueueContext.setTaskQueueIndexWriter(new TaskQueueIndexWriter(taskQueueContext));
    }

    @Override
    public void stop()
    {
        final TaskQueueIndexWriter taskQueueIndexWriter = taskQueueContext.getTaskQueueIndexWriter();
        taskQueueIndexWriter.update(Integer.MAX_VALUE);
        taskQueueIndexWriter.writeCheckpoints();
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

    public Injector<HashIndexManager<Long2LongHashIndex>> getLockedTasksIndexServiceInjector()
    {
        return lockedTasksIndexServiceInjector;
    }

    public TaskQueueContext getTaskQueueContext()
    {
        return taskQueueContext;
    }

    public Injector<HashIndexManager<Bytes2LongHashIndex>> getTaskTypeIndexServiceInjector()
    {
        return taskTypeIndexServiceInjector;
    }

}

package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.transport.worker.BrokerRequestWorkerContextService;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.ServiceStartContext;

public class TaskQueueWorkerContextService extends BrokerRequestWorkerContextService
{
    protected final Injector<TaskQueueManager> taskQueueManagerInjector = new Injector<>();

    protected TaskQueueWorkerContext taskQueueContext;

    public TaskQueueWorkerContextService(TaskQueueWorkerContext context)
    {
        super(context);
        this.taskQueueContext = context;
    }

    @Override
    public void start(ServiceStartContext ctx)
    {
        super.start(ctx);
        taskQueueContext.setTaskQueueManager(taskQueueManagerInjector.getValue());
    }

    public Injector<TaskQueueManager> getTaskQueueManagerInjector()
    {
        return taskQueueManagerInjector;
    }


}

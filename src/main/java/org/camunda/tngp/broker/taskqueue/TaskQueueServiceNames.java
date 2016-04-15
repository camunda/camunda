package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.servicecontainer.ServiceName;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.index.LogIndex;

public class TaskQueueServiceNames
{
    public final static ServiceName<TaskQueueManager> TASK_QUEUE_MANAGER = ServiceName.newServiceName("taskqueue.manager", TaskQueueManager.class);

    public final static ServiceName<TaskQueueContext> taskQueueContextServiceName(String taskQueueName)
    {
        return ServiceName.newServiceName(String.format("taskqueue.%s.context", taskQueueName), TaskQueueContext.class);
    }

    public final static ServiceName<IdGenerator> taskQueueIdGeneratorName(String taskQueueName)
    {
        return ServiceName.newServiceName(String.format("taskqueue.%s.id-generator", taskQueueName), IdGenerator.class);
    }

    public final static ServiceName<LogIndex> taskQueueLogIdIndexName(String taskQueueName)
    {
        return ServiceName.newServiceName(String.format("taskqueue.%s.id-index", taskQueueName), LogIndex.class);
    }

}

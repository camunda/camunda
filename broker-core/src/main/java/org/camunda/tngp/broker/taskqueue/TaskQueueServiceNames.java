package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.ServiceName;

public class TaskQueueServiceNames
{
    public static final ServiceName<TaskQueueManager> TASK_QUEUE_MANAGER = ServiceName.newServiceName("taskqueue.manager", TaskQueueManager.class);

    public static ServiceName<TaskQueueContext> taskQueueContextServiceName(String taskQueueName)
    {
        return ServiceName.newServiceName(String.format("taskqueue.%s.context", taskQueueName), TaskQueueContext.class);
    }

    public static ServiceName<IdGenerator> taskQueueIdGeneratorName(String taskQueueName)
    {
        return ServiceName.newServiceName(String.format("taskqueue.%s.id-generator", taskQueueName), IdGenerator.class);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ServiceName<HashIndexManager<Long2LongHashIndex>> taskQueueLockedTasksIndexServiceName(String taskQueueName)
    {
        return (ServiceName) ServiceName.newServiceName(String.format("taskqueue.%s.index.lockedTasks", taskQueueName), HashIndexManager.class);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ServiceName<HashIndexManager<Bytes2LongHashIndex>> taskQueueTaskTypePositionIndex(String taskQueueName)
    {
        return (ServiceName) ServiceName.newServiceName(String.format("taskqueue.%s.index.taskTypePosition", taskQueueName), HashIndexManager.class);
    }

}

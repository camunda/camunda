package org.camunda.tngp.broker.taskqueue;

import static org.camunda.tngp.broker.log.LogServiceNames.logServiceName;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.taskQueueContextServiceName;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.taskQueueIdGeneratorName;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.taskQueueLockedTasksIndexServiceName;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.taskQueueTaskTypePositionIndex;

import java.util.List;

import org.camunda.tngp.broker.services.Bytes2LongIndexManagerService;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.services.LogIdGeneratorService;
import org.camunda.tngp.broker.services.Long2LongIndexManagerService;
import org.camunda.tngp.broker.system.AbstractResourceContextProvider;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.taskqueue.cfg.TaskQueueCfg;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;
import org.camunda.tngp.servicecontainer.ServiceListener;
import org.camunda.tngp.servicecontainer.ServiceName;

public class TaskQueueManagerService extends AbstractResourceContextProvider<TaskQueueContext> implements
    Service<TaskQueueManager>,
    TaskQueueManager,
    ServiceListener
{
    protected ServiceContext serviceContext;

    protected final List<TaskQueueCfg> taskQueueCfgs;

    public TaskQueueManagerService(ConfigurationManager configurationManager)
    {
        super(TaskQueueContext.class);
        taskQueueCfgs = configurationManager.readList("task-queue", TaskQueueCfg.class);
    }

    @Override
    public void startTaskQueue(TaskQueueCfg taskQueueCfg)
    {
        final String taskQueueName = taskQueueCfg.name;
        if (taskQueueName == null || taskQueueName.isEmpty())
        {

            throw new RuntimeException("Cannot start task queue " + taskQueueName + ": Configuration property 'name' cannot be null.");
        }

        final int taskQueueId = taskQueueCfg.id;
        if (taskQueueId < 0 || taskQueueId > Short.MAX_VALUE)
        {
            throw new RuntimeException("Cannot start task queue " + taskQueueName + ": Invalid value for task queue id " + taskQueueId + ". Value must be in range [0," + Short.MAX_VALUE + "]");
        }

        final String logName = taskQueueCfg.logName;
        if (logName == null || logName.isEmpty())
        {
            throw new RuntimeException("Cannot start task queue " + taskQueueName + ": Mandatory configuration property 'logName' is not set.");
        }

        final ServiceName<Log> logServiceName = logServiceName(logName);
        final ServiceName<HashIndexManager<Long2LongHashIndex>> lockedTasksIndexServiceName = taskQueueLockedTasksIndexServiceName(taskQueueName);
        final ServiceName<HashIndexManager<Bytes2LongHashIndex>> taskQueueTaskTypePositionIndexName = taskQueueTaskTypePositionIndex(taskQueueName);
        final ServiceName<IdGenerator> idGeneratorName = taskQueueIdGeneratorName(taskQueueName);

        final LogIdGeneratorService logIdGeneratorService = new LogIdGeneratorService(new TaskInstanceIdReader());
        serviceContext.createService(idGeneratorName, logIdGeneratorService)
            .dependency(logServiceName, logIdGeneratorService.getLogInjector())
            .install();

        final Long2LongIndexManagerService lockedTasksIndexManagerService = new Long2LongIndexManagerService(2048, 64 * 1024);
        serviceContext.createService(lockedTasksIndexServiceName, lockedTasksIndexManagerService)
            .dependency(logServiceName, lockedTasksIndexManagerService.getLogInjector())
            .install();

        final Bytes2LongIndexManagerService taskTypePositionIndex = new Bytes2LongIndexManagerService(64, 32 * 1024, 256);
        serviceContext.createService(taskQueueTaskTypePositionIndexName, taskTypePositionIndex)
            .dependency(logServiceName, taskTypePositionIndex.getLogInjector())
            .install();

        final TaskQueueContextService taskQueueContextService = new TaskQueueContextService(taskQueueName, taskQueueId);
        serviceContext.createService(taskQueueContextServiceName(taskQueueName), taskQueueContextService)
            .dependency(taskQueueTaskTypePositionIndexName, taskQueueContextService.getTaskTypeIndexServiceInjector())
            .dependency(lockedTasksIndexServiceName, taskQueueContextService.getLockedTasksIndexServiceInjector())
            .dependency(logServiceName, taskQueueContextService.getLogInjector())
            .dependency(idGeneratorName, taskQueueContextService.getTaskInstanceIdGeneratorInjector())
            .listener(this)
            .install();
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        this.serviceContext = serviceContext;

        for (TaskQueueCfg taskQueueCfg : taskQueueCfgs)
        {
            startTaskQueue(taskQueueCfg);
        }
    }

    @Override
    public void stop()
    {
        // nothing to do
    }

    @Override
    public TaskQueueManager get()
    {
        return this;
    }

}

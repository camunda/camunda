package org.camunda.tngp.broker.taskqueue;

import static org.camunda.tngp.broker.log.LogServiceNames.*;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.*;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.services.Bytes2LongIndexManagerService;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.services.LogIdGeneratorService;
import org.camunda.tngp.broker.services.Long2LongIndexManagerService;
import org.camunda.tngp.broker.system.AbstractResourceContextProvider;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.taskqueue.cfg.TaskQueueCfg;
import org.camunda.tngp.broker.transport.TransportServiceNames;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceGroupReference;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class TaskQueueManagerService extends AbstractResourceContextProvider<TaskQueueContext> implements
    Service<TaskQueueManager>,
    TaskQueueManager
{
    protected ServiceStartContext serviceContext;

    protected final List<TaskQueueCfg> taskQueueCfgs;

    protected final List<LogConsumer> inputLogConsumers = new CopyOnWriteArrayList<>();

    protected final ServiceGroupReference<WfRuntimeContext> wfRuntimeContextsReference = ServiceGroupReference.<WfRuntimeContext>create()
        .onAdd((wfCtxName, wfCtx) ->
        {
            final WfInstanceLogProcessorService wfInstanceLogReaderService = new WfInstanceLogProcessorService();
            serviceContext
                .createService(TaskQueueServiceNames.workflowEventHandlerService(wfCtx.getResourceName()), wfInstanceLogReaderService)
                .dependency(wfCtxName, wfInstanceLogReaderService.getWfRuntimeContextInjector())
                .dependency(TaskQueueServiceNames.TASK_QUEUE_MANAGER, wfInstanceLogReaderService.getTaskQueueManagerInjector())
                .install();
        }).build();


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

        final LogIdGeneratorService logIdGeneratorService = new LogIdGeneratorService();
        serviceContext.createService(idGeneratorName, logIdGeneratorService)
            .dependency(logServiceName, logIdGeneratorService.getLogInjector())
            .install();

        final Long2LongIndexManagerService lockedTasksIndexManagerService = new Long2LongIndexManagerService(32448, 4 * 1024);
        serviceContext.createService(lockedTasksIndexServiceName, lockedTasksIndexManagerService)
            .dependency(logServiceName, lockedTasksIndexManagerService.getLogInjector())
            .install();

        final Bytes2LongIndexManagerService taskTypePositionIndex = new Bytes2LongIndexManagerService(64, 32 * 1024, 256);
        serviceContext.createService(taskQueueTaskTypePositionIndexName, taskTypePositionIndex)
            .dependency(logServiceName, taskTypePositionIndex.getLogInjector())
            .install();

        final TaskQueueContextService taskQueueContextService = new TaskQueueContextService(taskQueueName, taskQueueId);
        serviceContext.createService(taskQueueContextServiceName(taskQueueName), taskQueueContextService)
            .group(TASK_QUEUE_CONTEXT_SERVICE_GROUP_NAME)
            .dependency(taskQueueTaskTypePositionIndexName, taskQueueContextService.getTaskTypeIndexServiceInjector())
            .dependency(lockedTasksIndexServiceName, taskQueueContextService.getLockedTasksIndexServiceInjector())
            .dependency(logServiceName, taskQueueContextService.getLogInjector())
            .dependency(idGeneratorName, taskQueueContextService.getTaskInstanceIdGeneratorInjector())
            // TODO: this is a hack: this response pool serves multiple task queue contexts,
            //   therefore using the response pool to determine the latest pending task queue request (in the LogConsumer)
            //   does not work
            .dependency(workerResponsePoolServiceName(TaskQueueComponent.WORKER_NAME), taskQueueContextService.getResponsePoolServiceInjector())
            .dependency(workerDataFramePoolServiceName(TaskQueueComponent.WORKER_NAME), taskQueueContextService.getDataFramePoolInjector())
            .dependency(TransportServiceNames.TRANSPORT, taskQueueContextService.getTransportInjector())
            .install();
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        this.serviceContext = serviceContext;

        for (TaskQueueCfg taskQueueCfg : taskQueueCfgs)
        {
            startTaskQueue(taskQueueCfg);
        }
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        // nothing to do
    }

    @Override
    public TaskQueueManager get()
    {
        return this;
    }

    @Override
    public List<LogConsumer> getInputLogConsumers()
    {
        return inputLogConsumers;
    }

    @Override
    public void registerInputLogConsumer(LogConsumer logConsumer)
    {
        this.inputLogConsumers.add(logConsumer);
    }

    public ServiceGroupReference<WfRuntimeContext> getWfRuntimeContextsReference()
    {
        return wfRuntimeContextsReference;
    }

}

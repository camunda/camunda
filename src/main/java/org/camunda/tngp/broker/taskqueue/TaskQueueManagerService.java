package org.camunda.tngp.broker.taskqueue;

import static org.camunda.tngp.broker.log.LogServiceNames.*;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.camunda.tngp.broker.servicecontainer.Service;
import org.camunda.tngp.broker.servicecontainer.ServiceContext;
import org.camunda.tngp.broker.servicecontainer.ServiceListener;
import org.camunda.tngp.broker.servicecontainer.ServiceName;
import org.camunda.tngp.broker.services.LogIdGeneratorService;
import org.camunda.tngp.broker.services.LogIndexService;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.taskqueue.cfg.TaskQueueCfg;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.index.LogIndex;

import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;

public class TaskQueueManagerService implements
    Service<TaskQueueManager>,
    TaskQueueManager,
    ServiceListener
{
    protected ServiceContext serviceContext;

    protected final List<TaskQueueCfg> taskQueueCfgs;

    protected Int2ObjectHashMap<TaskQueueContext> taskQueueContextMap = new Int2ObjectHashMap<>();
    protected TaskQueueContext[] taskQueueContexts = new TaskQueueContext[0];

    public TaskQueueManagerService(ConfigurationManager configurationManager)
    {
        taskQueueCfgs = configurationManager.readList("task-queue", TaskQueueCfg.class);
    }

    @Override
    public void startTaskQueue(TaskQueueCfg taskQueueCfg)
    {
        final String taskQueueName = taskQueueCfg.name;
        if(taskQueueName == null || taskQueueName.isEmpty())
        {
            throw new RuntimeException("Cannot start task queue "+taskQueueName+": Configuration property 'name' cannot be null.");
        }

        final int taskQueueId = taskQueueCfg.id;
        if(taskQueueId < 0 || taskQueueId > Short.MAX_VALUE)
        {
            throw new RuntimeException("Cannot start task queue " + taskQueueName + ": Invalid value for task queue id "+ taskQueueId+ ". Value must be in range [0,"+Short.MAX_VALUE+"]");
        }

        final String logName = taskQueueCfg.logName;
        if(logName == null || logName.isEmpty())
        {
            throw new RuntimeException("Cannot start task queue "+taskQueueName+": Mandatory configuration property 'logName' is not set.");
        }

        final ServiceName<Log> logServiceName = logServiceName(logName);
        final ServiceName<LogIndex> idIndexName = taskQueueLogIdIndexName(taskQueueName);
        final ServiceName<IdGenerator> idGeneratorName = taskQueueIdGeneratorName(taskQueueName);

        final LogIndexService logIndexService = new LogIndexService(new TaskInstanceIndexer(), 32, 1024 * 128);
        serviceContext.installService(idIndexName, logIndexService)
            .dependency(logServiceName, logIndexService.getLogInjector())
            .done();

        final LogIdGeneratorService logIdGeneratorService = new LogIdGeneratorService(new TaskInstanceIdReader());
        serviceContext.installService(idGeneratorName, logIdGeneratorService)
            .dependency(logServiceName, logIdGeneratorService.getLogInjector())
            .done();

        final TaskQueueContextService taskQueueContextService = new TaskQueueContextService(taskQueueName, taskQueueId);
        serviceContext.installService(taskQueueContextServiceName(taskQueueName), taskQueueContextService)
            .dependency(idIndexName, taskQueueContextService.getTaskInstanceIdIndexInjector())
            .dependency(logServiceName, taskQueueContextService.getLogInjector())
            .dependency(idGeneratorName, taskQueueContextService.getTaskInstanceIdGeneratorInjector())
            .listener(this)
            .done();
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

    @Override
    public synchronized <S> void onServiceStarted(ServiceName<S> name, S service)
    {
        final TaskQueueContext taskQueueContext = (TaskQueueContext) service;
        taskQueueContextMap.put(taskQueueContext.getTaskQueueId(), taskQueueContext);

        final List<TaskQueueContext> list = new ArrayList<TaskQueueContext>(Arrays.asList(taskQueueContexts));
        list.add(taskQueueContext);
        this.taskQueueContexts = list.toArray(new TaskQueueContext[list.size()]);
    }

    @Override
    public synchronized <S> void onServiceStopping(ServiceName<S> name, S service)
    {
        final TaskQueueContext taskQueueContext = (TaskQueueContext) service;
        taskQueueContextMap.remove(taskQueueContext.getTaskQueueId());

        final List<TaskQueueContext> list = new ArrayList<TaskQueueContext>(Arrays.asList(taskQueueContexts));
        list.remove(taskQueueContext);
        this.taskQueueContexts = list.toArray(new TaskQueueContext[list.size()]);
    }

    @Override
    public TaskQueueContext getContextForResource(int id)
    {
        return taskQueueContextMap.get(id);
    }

    @Override
    public TaskQueueContext[] getTaskQueueContexts()
    {
        return taskQueueContexts;
    }
}

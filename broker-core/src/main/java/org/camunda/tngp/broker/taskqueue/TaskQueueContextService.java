package org.camunda.tngp.broker.taskqueue;

import java.util.Arrays;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.log.LogWriter;
import org.camunda.tngp.broker.log.LogWritersImpl;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.taskqueue.log.handler.TaskInstanceHandler;
import org.camunda.tngp.broker.taskqueue.log.handler.TaskInstanceRequestHandler;
import org.camunda.tngp.broker.taskqueue.log.idx.LockedTasksIndexWriter;
import org.camunda.tngp.broker.taskqueue.log.idx.TaskTypeIndexWriter;
import org.camunda.tngp.broker.taskqueue.subscription.LockTasksOperator;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.BufferedLogReader;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;
import org.camunda.tngp.transport.singlemessage.DataFramePool;

public class TaskQueueContextService implements Service<TaskQueueContext>
{
    protected final Injector<Log> logInjector = new Injector<>();
    protected final Injector<IdGenerator> taskInstanceIdGeneratorInjector = new Injector<>();
    protected final Injector<HashIndexManager<Long2LongHashIndex>> lockedTasksIndexServiceInjector = new Injector<>();
    protected final Injector<HashIndexManager<Bytes2LongHashIndex>> taskTypeIndexServiceInjector = new Injector<>();

    protected final Injector<DeferredResponsePool> responsePoolServiceInjector = new Injector<>();

    protected final Injector<DataFramePool> dataFramePoolInjector = new Injector<>();
    protected final Injector<Transport> transportInjector = new Injector<>();

    protected final TaskQueueContext taskQueueContext;

    public TaskQueueContextService(String taskQueueName, int taskQueueId)
    {
        taskQueueContext = new TaskQueueContext(taskQueueName, taskQueueId);
    }

    @Override
    public void start(ServiceStartContext ctx)
    {
        ctx.run(() ->
        {
            final HashIndexManager<Long2LongHashIndex> lockedTasksIndexManager = lockedTasksIndexServiceInjector.getValue();
            final HashIndexManager<Bytes2LongHashIndex> taskTypeIndexManager = taskTypeIndexServiceInjector.getValue();
            final DeferredResponsePool responsePool = responsePoolServiceInjector.getValue();

            taskQueueContext.setLog(logInjector.getValue());
            taskQueueContext.setTaskInstanceIdGenerator(taskInstanceIdGeneratorInjector.getValue());
            taskQueueContext.setTaskTypePositionIndex(taskTypeIndexManager);

            final Log log = logInjector.getValue();

            final LogWriter logWriter = new LogWriter(log);
            taskQueueContext.setLogWriter(logWriter);

            final Templates templates = Templates.taskQueueLogTemplates();
            final LogConsumer taskProcessor = new LogConsumer(
                    log.getId(),
                    new BufferedLogReader(log),
                    responsePool,
                    templates,
                    new LogWritersImpl(taskQueueContext, null));

            final LockTasksOperator lockTasksOperator = new LockTasksOperator(
                    taskTypeIndexManager.getIndex(),
                    new BufferedLogReader(log),
                    logWriter,
                    dataFramePoolInjector.getValue(),
                    responsePool.getCapacity() // there cannot be more open adhoc subscriptions than there are requests
                    );

            final Transport transport = transportInjector.getValue();
            transport.registerChannelListener(lockTasksOperator);

            taskProcessor.addHandler(Templates.TASK_INSTANCE, new TaskInstanceHandler(lockTasksOperator));
            taskProcessor.addHandler(Templates.TASK_INSTANCE_REQUEST, new TaskInstanceRequestHandler(new BufferedLogReader(log), lockedTasksIndexManager.getIndex()));

            taskProcessor.addIndexWriter(new TaskTypeIndexWriter(taskTypeIndexManager, templates));
            taskProcessor.addIndexWriter(new LockedTasksIndexWriter(lockedTasksIndexManager, templates));

            taskProcessor.recover(Arrays.asList(new BufferedLogReader(log)));

            // replay all events before taking new requests;
            // avoids that we mix up new API requests (that require a response)
            // with existing API requests (that do not require a response anymore)
            taskProcessor.fastForwardToLastEvent();

            taskQueueContext.setLogConsumer(taskProcessor);

            taskQueueContext.setLockedTasksOperator(lockTasksOperator);
        });
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.run(() ->
        {
            taskQueueContext.getLogConsumer().writeSavepoints();
            final Transport transport = transportInjector.getValue();
            transport.removeChannelListener(taskQueueContext.getLockedTasksOperator());
        });
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

    public Injector<DeferredResponsePool> getResponsePoolServiceInjector()
    {
        return responsePoolServiceInjector;
    }

    public Injector<DataFramePool> getDataFramePoolInjector()
    {
        return dataFramePoolInjector;
    }

    public Injector<Transport> getTransportInjector()
    {
        return transportInjector;
    }
}

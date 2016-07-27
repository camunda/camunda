package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.idx.IndexWriter;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReaderImpl;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

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

        final Log log = logInjector.getValue();
        final HashIndexManager<Long2LongHashIndex> lockedTasksIndexManager = taskQueueContext.getLockedTaskInstanceIndex();
        final HashIndexManager<Bytes2LongHashIndex> taskTypeIndexManager = taskQueueContext.getTaskTypePositionIndex();

        final TaskQueueIndexLogTracker taskQueueIndexWriter =
                new TaskQueueIndexLogTracker(lockedTasksIndexManager.getIndex(), taskTypeIndexManager.getIndex());
        final IndexWriter<TaskInstanceReader> indexWriter = new IndexWriter<>(
                new LogReaderImpl(log),
                log.getWriteBuffer().openSubscription(),
                log.getId(),
                new TaskInstanceReader(),
                taskQueueIndexWriter,
                new HashIndexManager<?>[]{lockedTasksIndexManager, taskTypeIndexManager});
        indexWriter.resetToLastCheckpointPosition();
        taskQueueContext.setIndexWriter(indexWriter);
        taskQueueContext.setLogWriter(new LogWriter(log, indexWriter));
    }

    @Override
    public void stop()
    {
        final IndexWriter<TaskInstanceReader> taskQueueIndexWriter = taskQueueContext.getIndexWriter();
        taskQueueIndexWriter.indexLogEntries();
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

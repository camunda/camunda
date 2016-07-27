package org.camunda.tngp.broker.wf.repository;

import org.camunda.tngp.broker.idx.IndexWriter;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.repository.idx.WfTypeIndexLogTracker;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionReader;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReaderImpl;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class WfRepositoryContextService implements Service<WfRepositoryContext>
{
    protected final Injector<Log> wfDefinitionLogInjector = new Injector<>();
    protected final Injector<IdGenerator> wfDefinitionIdGeneratorInjector = new Injector<>();
    protected final Injector<HashIndexManager<Bytes2LongHashIndex>> wfDefinitionKeyIndexInjector = new Injector<>();
    protected final Injector<HashIndexManager<Long2LongHashIndex>> wfDefinitionIdIndexInjector = new Injector<>();
    protected final Injector<WfDefinitionCache> wfDefinitionCacheInjector = new Injector<>();

    protected final WfRepositoryContext context;

    public WfRepositoryContextService(int id, String name)
    {
        this.context = new WfRepositoryContext(id, name);
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        final Log log = wfDefinitionLogInjector.getValue();
        final HashIndexManager<Bytes2LongHashIndex> wfDefinitionKeyIndexManager = wfDefinitionKeyIndexInjector.getValue();
        final HashIndexManager<Long2LongHashIndex> wfDefinitionIdIndexManager = wfDefinitionIdIndexInjector.getValue();

        context.setWfDefinitionLog(log);
        context.setWfDefinitionIdGenerator(wfDefinitionIdGeneratorInjector.getValue());
        context.setWfDefinitionKeyIndex(wfDefinitionKeyIndexManager);
        context.setWfDefinitionIdIndex(wfDefinitionIdIndexManager);
        context.setWfDefinitionCacheService(wfDefinitionCacheInjector.getValue());

        final WfTypeIndexLogTracker indexLogTracker = new WfTypeIndexLogTracker(wfDefinitionIdIndexManager.getIndex(), wfDefinitionKeyIndexManager.getIndex());

        final IndexWriter<WfDefinitionReader> indexWriter = new IndexWriter<>(
                new LogReaderImpl(log),
                log.getWriteBuffer().openSubscription(),
                log.getId(),
                new WfDefinitionReader(),
                indexLogTracker,
                new HashIndexManager<?>[]{wfDefinitionIdIndexManager, wfDefinitionKeyIndexManager});
        context.setIndexWriter(indexWriter);
        context.setLogWriter(new LogWriter(log, indexWriter));
    }

    @Override
    public void stop()
    {
        final IndexWriter<WfDefinitionReader> indexWriter = context.getIndexWriter();
        indexWriter.indexLogEntries();
        indexWriter.writeCheckpoints();
    }

    @Override
    public WfRepositoryContext get()
    {
        return context;
    }

    public Injector<Log> getWfDefinitionLogInjector()
    {
        return wfDefinitionLogInjector;
    }

    public Injector<IdGenerator> getWfDefinitionIdGeneratorInjector()
    {
        return wfDefinitionIdGeneratorInjector;
    }

    public Injector<HashIndexManager<Bytes2LongHashIndex>> getWfDefinitionKeyIndexInjector()
    {
        return wfDefinitionKeyIndexInjector;
    }

    public Injector<HashIndexManager<Long2LongHashIndex>> getWfDefinitionIdIndexInjector()
    {
        return wfDefinitionIdIndexInjector;
    }

    public Injector<WfDefinitionCache> getWfDefinitionCacheInjector()
    {
        return wfDefinitionCacheInjector;
    }
}

package org.camunda.tngp.broker.wf.repository;

import java.util.function.LongFunction;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.bpmn.graph.transformer.BpmnModelInstanceTransformer;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionReader;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogEntryReader;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.collections.Long2ObjectCache;

public class WfDefinitionCacheService implements Service<WfDefinitionCacheService>, LongFunction<ProcessGraph>
{
    protected final Long2ObjectCache<ProcessGraph> cache;

    protected final Injector<Log> wfDefinitionLogInjector = new Injector<>();
    protected final Injector<HashIndexManager<Long2LongHashIndex>> wfDefinitionIdIndexInjector = new Injector<>();
    protected final Injector<HashIndexManager<Bytes2LongHashIndex>> wfDefinitionKeyIndexInjector = new Injector<>();

    protected final BpmnModelInstanceTransformer transformer = new BpmnModelInstanceTransformer();

    protected final LogEntryReader logEntryReader = new LogEntryReader(WfDefinitionReader.MAX_LENGTH);
    protected final WfDefinitionReader reader = new WfDefinitionReader();

    public WfDefinitionCacheService(int numSets, int setSize)
    {
        cache = new Long2ObjectCache<>(numSets, setSize, (v) ->
        {
            // nothing to do
        });
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        // nothing to do
    }

    @Override
    public void stop()
    {
        // nothing to do
    }

    @Override
    public WfDefinitionCacheService get()
    {
        return this;
    }

    public ProcessGraph getLatestProcessGraphByTypeKey(DirectBuffer buffer, int offset, int length)
    {
        // TODO: throw exception if key longer

        final Bytes2LongHashIndex keyIndex = wfDefinitionKeyIndexInjector.getValue().getIndex();
        final long id = keyIndex.get(buffer, 0, length, -1);

        ProcessGraph processGraph = null;

        if (id != -1)
        {
            processGraph = getProcessGraphByTypeId(id);
        }

        return processGraph;
    }

    public ProcessGraph getProcessGraphByTypeId(long id)
    {
        return cache.computeIfAbsent(id, this);
    }

    @Override
    public ProcessGraph apply(long key)
    {
        final Long2LongHashIndex idIndex = wfDefinitionIdIndexInjector.getValue().getIndex();
        final Log log = wfDefinitionLogInjector.getValue();
        final long position = idIndex.get(key, -1);

        ProcessGraph graph = null;

        if (position != -1)
        {
            logEntryReader.read(log, position, reader);
            graph = transformer.transformSingleProcess(reader.asModelInstance(), key);
        }

        return graph;
    }

    public Injector<HashIndexManager<Long2LongHashIndex>> getWfDefinitionIdIndexInjector()
    {
        return wfDefinitionIdIndexInjector;
    }

    public Injector<HashIndexManager<Bytes2LongHashIndex>> getWfDefinitionKeyIndexInjector()
    {
        return wfDefinitionKeyIndexInjector;
    }

    public Injector<Log> getWfDefinitionLogInjector()
    {
        return wfDefinitionLogInjector;
    }

}

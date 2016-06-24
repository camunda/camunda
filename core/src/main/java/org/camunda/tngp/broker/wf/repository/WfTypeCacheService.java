package org.camunda.tngp.broker.wf.repository;

import java.util.Arrays;
import java.util.function.LongFunction;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.bpmn.graph.transformer.BpmnModelInstanceTransformer;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.repository.handler.DeployBpmnResourceHandler;
import org.camunda.tngp.broker.wf.repository.log.WfTypeReader;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogEntryReader;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.collections.Long2ObjectCache;

public class WfTypeCacheService implements Service<WfTypeCacheService>, LongFunction<ProcessGraph>
{
    protected final Long2ObjectCache<ProcessGraph> cache;

    protected final Injector<Log> wfTypeLogInjector = new Injector<>();
    protected final Injector<HashIndexManager<Long2LongHashIndex>> wfTypeIdIndexInjector = new Injector<>();
    protected final Injector<HashIndexManager<Bytes2LongHashIndex>> wfTypeKeyIndexInjector = new Injector<>();

    protected final BpmnModelInstanceTransformer transformer = new BpmnModelInstanceTransformer();

    protected final LogEntryReader logEntryReader = new LogEntryReader(WfTypeReader.MAX_LENGTH);
    protected final WfTypeReader reader = new WfTypeReader();

    protected byte[] keyBuffer = new byte[DeployBpmnResourceHandler.WF_TYPE_KEY_MAX_LENGTH];

    public WfTypeCacheService(int numSets, int setSize)
    {
        cache = new Long2ObjectCache<>(numSets, setSize, (v) ->
        {
            // nothin to do
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
    public WfTypeCacheService get()
    {
        return this;
    }

    public ProcessGraph getLatestProcessGraphByTypeKey(DirectBuffer buffer, int offset, int length)
    {
        // TODO: throw exception if key longer

        buffer.getBytes(offset, keyBuffer, 0, length);

        if (length <= keyBuffer.length)
        {
            Arrays.fill(keyBuffer, length, keyBuffer.length, (byte) 0);
        }

        final Bytes2LongHashIndex keyIndex = wfTypeKeyIndexInjector.getValue().getIndex();
        final long id = keyIndex.get(keyBuffer, -1);

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
        final Long2LongHashIndex idIndex = wfTypeIdIndexInjector.getValue().getIndex();
        final Log log = wfTypeLogInjector.getValue();
        final long position = idIndex.get(key, -1);

        ProcessGraph graph = null;

        if (position != -1)
        {
            logEntryReader.read(log, position, reader);
            graph = transformer.transformSingleProcess(reader.asModelInstance(), key);
        }

        return graph;
    }

    public Injector<HashIndexManager<Long2LongHashIndex>> getWfTypeIdIndexInjector()
    {
        return wfTypeIdIndexInjector;
    }

    public Injector<HashIndexManager<Bytes2LongHashIndex>> getWfTypeKeyIndexInjector()
    {
        return wfTypeKeyIndexInjector;
    }

    public Injector<Log> getWfTypeLogInjector()
    {
        return wfTypeLogInjector;
    }

}

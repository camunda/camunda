package org.camunda.tngp.broker.wf.repository;

import java.util.function.LongFunction;

import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.bpmn.graph.transformer.BpmnModelInstanceTransformer;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionReader;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogEntryReader;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.collections.Long2ObjectCache;

public class WfDefinitionCache implements LongFunction<ProcessGraph>
{
    protected final Long2ObjectCache<ProcessGraph> cache;

    protected final Log wfTypeLog;
    protected final Long2LongHashIndex wfTypeIdIndex;
    protected final Bytes2LongHashIndex wfTypeKeyIndex;

    protected final BpmnModelInstanceTransformer transformer = new BpmnModelInstanceTransformer();

    protected final LogEntryReader logEntryReader = new LogEntryReader(WfDefinitionReader.MAX_LENGTH);
    protected final WfDefinitionReader reader = new WfDefinitionReader();

    public WfDefinitionCache(int numSets, int setSize, Log wfTypeLog, Long2LongHashIndex wfTypeIdIndex, Bytes2LongHashIndex wfTypeKeyIndex)
    {
        cache = new Long2ObjectCache<>(numSets, setSize, (v) ->
        {
            // nothin to do
        });

        this.wfTypeIdIndex = wfTypeIdIndex;
        this.wfTypeKeyIndex = wfTypeKeyIndex;
        this.wfTypeLog = wfTypeLog;
    }

    public ProcessGraph getLatestProcessGraphByTypeKey(DirectBuffer buffer, int offset, int length)
    {
        // TODO: throw exception if key longer

        final long id = wfTypeKeyIndex.get(buffer, 0, length, -1);

        ProcessGraph processGraph = null;

        if (id >= 0)
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
        final long position = wfTypeIdIndex.get(key, -1);

        ProcessGraph graph = null;

        if (position >= 0)
        {
            logEntryReader.read(wfTypeLog, position, reader);
            graph = transformer.transformSingleProcess(reader.asModelInstance(), key);
        }

        return graph;
    }


}

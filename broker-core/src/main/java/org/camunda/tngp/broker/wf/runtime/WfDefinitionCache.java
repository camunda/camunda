package org.camunda.tngp.broker.wf.runtime;

import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectCache;
import org.agrona.io.DirectBufferInputStream;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.bpmn.graph.transformer.BpmnModelInstanceTransformer;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.BufferedLogReader;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.ReadableLogEntry;
import org.camunda.tngp.protocol.wf.WfDefinitionReader;

public class WfDefinitionCache implements LongFunction<ProcessGraph>
{
    protected final Long2ObjectCache<ProcessGraph> cache;

    protected final Log wfTypeLog;
    protected final Long2LongHashIndex wfTypeIdIndex;
    protected final Bytes2LongHashIndex wfTypeKeyIndex;

    protected final BpmnModelInstanceTransformer transformer = new BpmnModelInstanceTransformer();

    protected final WfDefinitionReader reader = new WfDefinitionReader();

    protected final LogReader logReader = new BufferedLogReader();

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
            logReader.wrap(wfTypeLog, position);
            final ReadableLogEntry logEntry = logReader.next();
            logEntry.readValue(reader);

            final BpmnModelInstance modelInstance = Bpmn.readModelFromStream(new DirectBufferInputStream(reader.getResource()));
            graph = transformer.transformSingleProcess(modelInstance, key);
        }

        return graph;
    }

}

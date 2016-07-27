package org.camunda.tngp.broker.wf.repository.idx;

import java.util.function.LongConsumer;

import org.camunda.tngp.broker.idx.LogEntryTracker;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionReader;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.util.BiLongConsumer;

import uk.co.real_logic.agrona.DirectBuffer;

public class WfTypeIndexLogTracker implements LogEntryTracker<WfDefinitionReader>
{
    protected final Long2LongHashIndex wfTypeIdIndex;
    protected final Bytes2LongHashIndex wfTypeKeyIndex;

    public WfTypeIndexLogTracker(Long2LongHashIndex wfTypeIdIndex, Bytes2LongHashIndex wfTypeKeyIndex)
    {
        this.wfTypeIdIndex = wfTypeIdIndex;
        this.wfTypeKeyIndex = wfTypeKeyIndex;
    }

    /*
     * Note: indexes on the workflow repository log are currently never made dirty.
     * This means, that during deployment of a new workflow definition,
     * starting an instance of that new version is not possible
     * and instead the old version is started (or an error is returned in case the ID is used)
     */

    @Override
    public void onLogEntryStaged(WfDefinitionReader logEntry)
    {
    }

    @Override
    public void onLogEntryFailed(WfDefinitionReader logEntry)
    {
    }

    @Override
    public void onLogEntryCommit(WfDefinitionReader logEntry, final long position)
    {
        onLogEntry(logEntry,
            (id) -> wfTypeIdIndex.put(id, position),
            (key, id) -> wfTypeKeyIndex.put(key, 0, key.capacity(), id));
    }

    public void onLogEntry(WfDefinitionReader logEntry,
            LongConsumer onWorkflowTypeId,
            BiLongConsumer<DirectBuffer> onWorkflowTypeKey)
    {
        final long id = logEntry.id();

        onWorkflowTypeId.accept(id);

        final DirectBuffer typeKey = logEntry.getTypeKey();

        onWorkflowTypeKey.accept(typeKey, id);

    }

}

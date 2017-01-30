package org.camunda.tngp.broker.taskqueue.processor;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.HashIndexSnapshotSupport;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.hashindex.Long2BytesHashIndex;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;

public class TaskInstanceStreamProcessor implements StreamProcessor
{
    protected static final int INDEX_VALUE_LENGTH = SIZE_OF_LONG + SIZE_OF_INT;
    protected static final int INDEX_POSITION_OFFSET = 0;
    protected static final int INDEX_STATE_OFFSET = SIZE_OF_LONG;

    protected final BrokerEventMetadata sourceEventMetadata = new BrokerEventMetadata();
    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

    protected final CommandResponseWriter responseWriter;
    protected final IndexStore indexStore;
    protected final UnsafeBuffer indexValueWriteBuffer = new UnsafeBuffer(new byte[INDEX_VALUE_LENGTH]);
    protected final UnsafeBuffer indexValueReadBuffer = new UnsafeBuffer(new byte[INDEX_VALUE_LENGTH]);

    protected final CreateTaskProcessor createTaskProcessor = new CreateTaskProcessor();
    protected final LockTaskProcessor lockTaskProcessor = new LockTaskProcessor();

    protected final Long2BytesHashIndex taskIndex;
    protected final HashIndexSnapshotSupport<Long2BytesHashIndex> indexSnapshotSupport;

    protected final TaskEvent taskEvent = new TaskEvent();

    protected int streamId;

    protected LoggedEvent event;
    protected long eventPosition = 0;
    protected long eventKey = 0;

    public TaskInstanceStreamProcessor(CommandResponseWriter responseWriter, IndexStore indexStore)
    {
        this.responseWriter = responseWriter;
        this.indexStore = indexStore;

        taskIndex = new Long2BytesHashIndex(indexStore, 100, 1, INDEX_VALUE_LENGTH);
        indexSnapshotSupport = new HashIndexSnapshotSupport<>(taskIndex, indexStore);
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return indexSnapshotSupport;
    }

    public void onOpen(StreamProcessorContext context)
    {
        streamId = context.getSourceStream().getId();
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        this.event = event;

        eventPosition = event.getPosition();
        eventKey = event.getLongKey();

        event.readMetadata(sourceEventMetadata);

        taskEvent.reset();
        event.readValue(taskEvent);

        EventProcessor eventProcessor = null;

        switch (taskEvent.getEventType())
        {
            case CREATE:
                eventProcessor = createTaskProcessor;
                break;
            case LOCK:
                eventProcessor = lockTaskProcessor;
                break;

            default:
                break;
        }

        return eventProcessor;
    }

    @Override
    public void afterEvent()
    {
        taskEvent.reset();
    }

    class CreateTaskProcessor implements EventProcessor
    {

        @Override
        public void processEvent()
        {
            taskEvent.setEventType(TaskEventType.CREATED);
        }

        @Override
        public boolean executeSideEffects()
        {
            boolean success = true;

            success = responseWriter.brokerEventMetadata(sourceEventMetadata)
                .topicId(streamId)
                .longKey(eventKey)
                .eventWriter(taskEvent)
                .tryWriteResponse();

            return success;
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            targetEventMetadata.reset();
            // TODO: targetEventMetadata.raftTermId(raftTermId);

            return writer.key(eventKey)
                .metadataWriter(targetEventMetadata)
                .valueWriter(taskEvent)
                .tryWrite();
        }

        @Override
        public void updateState()
        {
            updateIndex(eventKey, eventPosition, TaskEventType.CREATED);
        }
    }

    class LockTaskProcessor implements EventProcessor
    {
        protected boolean isLocked;

        @Override
        public void processEvent()
        {
            isLocked = false;

            final byte[] eventData = taskIndex.get(eventKey);

            if (eventData != null && taskEvent.getLockTime() > 0)
            {
                indexValueReadBuffer.wrap(eventData);
                final int stateId = indexValueReadBuffer.getInt(INDEX_STATE_OFFSET);

                if (stateId == TaskEventType.CREATED.id())
                {
                    taskEvent.setEventType(TaskEventType.LOCKED);
                    isLocked = true;
                }
            }

            if (!isLocked)
            {
                taskEvent.setEventType(TaskEventType.LOCK_FAILED);
            }
        }

        @Override
        public boolean executeSideEffects()
        {
            boolean success = true;

            if (isLocked)
            {
                success = responseWriter.brokerEventMetadata(sourceEventMetadata)
                    .topicId(streamId)
                    .longKey(eventKey)
                    .eventWriter(taskEvent)
                    .tryWriteResponse();
            }
            return success;
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            targetEventMetadata.reset();
            // TODO: targetEventMetadata.raftTermId(raftTermId);

            return writer.key(eventKey)
                .metadataWriter(targetEventMetadata)
                .valueWriter(taskEvent)
                .tryWrite();
        }

        @Override
        public void updateState()
        {
            if (isLocked)
            {
                updateIndex(eventKey, eventPosition, TaskEventType.LOCKED);
            }
        }
    }

    protected void updateIndex(long eventKey, long eventPosition, TaskEventType eventType)
    {
        indexValueWriteBuffer.putLong(INDEX_POSITION_OFFSET, eventPosition);
        indexValueWriteBuffer.putInt(INDEX_STATE_OFFSET, eventType.id());

        taskIndex.put(eventKey, indexValueWriteBuffer.byteArray());
    }

}

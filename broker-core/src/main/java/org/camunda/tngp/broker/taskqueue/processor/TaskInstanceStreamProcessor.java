package org.camunda.tngp.broker.taskqueue.processor;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;

import java.nio.ByteOrder;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.HashIndexSnapshotSupport;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.taskqueue.TaskSubscriptionManager;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.hashindex.Long2BytesHashIndex;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.util.time.ClockUtil;

public class TaskInstanceStreamProcessor implements StreamProcessor
{
    protected static final int INDEX_VALUE_LENGTH = 2 * SIZE_OF_INT;
    protected static final int INDEX_STATE_OFFSET = 0;
    protected static final int INDEX_LOCK_OWNER_OFFSET = INDEX_STATE_OFFSET + SIZE_OF_INT;

    protected BrokerEventMetadata sourceEventMetadata = new BrokerEventMetadata();
    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

    protected final CommandResponseWriter responseWriter;
    protected final SubscribedEventWriter subscribedEventWriter;
    protected final IndexStore indexStore;
    protected final TaskSubscriptionManager taskSubscriptionManager;

    protected final CreateTaskProcessor createTaskProcessor = new CreateTaskProcessor();
    protected final LockTaskProcessor lockTaskProcessor = new LockTaskProcessor();
    protected final CompleteTaskProcessor completeTaskProcessor = new CompleteTaskProcessor();
    protected final FailTaskProcessor failTaskProcessor = new FailTaskProcessor();
    protected final ExpireLockTaskProcessor expireLockTaskProcessor = new ExpireLockTaskProcessor();
    protected final UpdateRetriesTaskProcessor updateRetriesTaskProcessor = new UpdateRetriesTaskProcessor();

    protected final Long2BytesHashIndex taskIndex;
    protected final HashIndexSnapshotSupport<Long2BytesHashIndex> indexSnapshotSupport;

    protected final IndexAccessor indexAccessor = new IndexAccessor();
    protected final IndexWriter indexWriter = new IndexWriter();

    protected final TaskEvent taskEvent = new TaskEvent();

    protected int streamId;

    protected long eventPosition = 0;
    protected long eventKey = 0;
    protected long sourceEventPosition = 0;

    public TaskInstanceStreamProcessor(CommandResponseWriter responseWriter, SubscribedEventWriter subscribedEventWriter, IndexStore indexStore, TaskSubscriptionManager taskSubscriptionManager)
    {
        this.responseWriter = responseWriter;
        this.subscribedEventWriter = subscribedEventWriter;
        this.indexStore = indexStore;
        this.taskSubscriptionManager = taskSubscriptionManager;

        taskIndex = new Long2BytesHashIndex(indexStore, Short.MAX_VALUE, 256, INDEX_VALUE_LENGTH);
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

    public static MetadataFilter eventFilter()
    {
        return (m) -> m.getEventType() == EventType.TASK_EVENT;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        eventPosition = event.getPosition();
        eventKey = event.getLongKey();
        sourceEventPosition = event.getSourceEventPosition();

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
            case COMPLETE:
                eventProcessor = completeTaskProcessor;
                break;
            case FAIL:
                eventProcessor = failTaskProcessor;
                break;
            case EXPIRE_LOCK:
                eventProcessor = expireLockTaskProcessor;
                break;
            case UPDATE_RETRIES:
                eventProcessor = updateRetriesTaskProcessor;
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

    protected boolean writeResponse()
    {
        return responseWriter
            .brokerEventMetadata(sourceEventMetadata)
            .topicId(streamId)
            .longKey(eventKey)
            .eventWriter(taskEvent)
            .tryWriteResponse();
    }

    protected long writeEventToLogStream(LogStreamWriter writer)
    {
        targetEventMetadata.reset();
        targetEventMetadata
            .protocolVersion(Constants.PROTOCOL_VERSION)
            .eventType(TASK_EVENT);

        // TODO: targetEventMetadata.raftTermId(raftTermId);

        return writer
            .key(eventKey)
            .metadataWriter(targetEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
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

            if (sourceEventMetadata.hasRequestMetadata())
            {
                success = writeResponse();
            }
            return success;
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeEventToLogStream(writer);
        }

        @Override
        public void updateState()
        {
            indexWriter.write(eventKey, TaskEventType.CREATED, -1, -1L);
        }
    }

    class LockTaskProcessor implements EventProcessor
    {
        protected boolean isLocked;
        protected long writtenEventPosition;

        @Override
        public void processEvent()
        {
            isLocked = false;

            indexAccessor.wrapIndexKey(eventKey);
            final int typeId = indexAccessor.getTypeId();

            final boolean isLockable = typeId == TaskEventType.CREATED.id() || typeId == TaskEventType.FAILED.id() || typeId == TaskEventType.LOCK_EXPIRED.id();

            if (isLockable && taskEvent.getLockTime() > ClockUtil.getCurrentTimeInMillis())
            {
                taskEvent.setEventType(TaskEventType.LOCKED);
                isLocked = true;
            }

            if (!isLocked)
            {
                taskEvent.setEventType(TaskEventType.LOCK_REJECTED);
            }
        }

        @Override
        public boolean executeSideEffects()
        {
            boolean success = true;

            if (isLocked)
            {
                success = subscribedEventWriter
                        .channelId(sourceEventMetadata.getReqChannelId())
                        .topicId(streamId)
                        .longKey(eventKey)
                        .subscriberKey(sourceEventMetadata.getSubscriberKey())
                        .subscriptionType(SubscriptionType.TASK_SUBSCRIPTION)
                        .eventType(TASK_EVENT)
                        .eventWriter(taskEvent)
                        .tryWriteMessage();
            }
            else
            {
                final long subscriptionId = sourceEventMetadata.getSubscriberKey();

                taskSubscriptionManager.increaseSubscriptionCredits(subscriptionId, 1);

                success = true;
            }

            return success;
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            writtenEventPosition = writeEventToLogStream(writer);

            return writtenEventPosition;
        }

        @Override
        public void updateState()
        {
            if (isLocked)
            {
                indexWriter.write(eventKey, TaskEventType.LOCKED, taskEvent.getLockOwner(), writtenEventPosition);
            }
        }
    }

    class CompleteTaskProcessor implements EventProcessor
    {
        protected boolean isCompleted;

        @Override
        public void processEvent()
        {
            isCompleted = false;

            indexAccessor.wrapIndexKey(eventKey);
            final int typeId = indexAccessor.getTypeId();
            final int lockOwner = indexAccessor.getLockOwner();

            final boolean isCompletable = typeId == TaskEventType.LOCKED.id() || typeId == TaskEventType.LOCK_EXPIRED.id();

            if (isCompletable && lockOwner == taskEvent.getLockOwner())
            {
                taskEvent.setEventType(TaskEventType.COMPLETED);
                isCompleted = true;
            }

            if (!isCompleted)
            {
                taskEvent.setEventType(TaskEventType.COMPLETE_REJECTED);
            }
        }

        @Override
        public boolean executeSideEffects()
        {
            return writeResponse();
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeEventToLogStream(writer);
        }

        @Override
        public void updateState()
        {
            if (isCompleted)
            {
                indexWriter.write(eventKey, TaskEventType.COMPLETED, -1, -1);
            }
        }
    }

    class FailTaskProcessor implements EventProcessor
    {
        protected boolean isFailed;

        @Override
        public void processEvent()
        {
            isFailed = false;

            indexAccessor.wrapIndexKey(eventKey);
            final int typeId = indexAccessor.getTypeId();
            final int lockOwner = indexAccessor.getLockOwner();

            if (typeId == TaskEventType.LOCKED.id() && lockOwner == taskEvent.getLockOwner())
            {
                taskEvent.setEventType(TaskEventType.FAILED);
                isFailed = true;
            }

            if (!isFailed)
            {
                taskEvent.setEventType(TaskEventType.FAIL_REJECTED);
            }
        }

        @Override
        public boolean executeSideEffects()
        {
            return writeResponse();
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeEventToLogStream(writer);
        }

        @Override
        public void updateState()
        {
            if (isFailed)
            {
                indexWriter.write(eventKey, TaskEventType.FAILED, -1, -1);
            }
        }
    }

    class ExpireLockTaskProcessor implements EventProcessor
    {
        protected boolean isExpired;

        @Override
        public void processEvent()
        {
            isExpired = false;

            indexAccessor.wrapIndexKey(eventKey);
            final int typeId = indexAccessor.getTypeId();

            if (typeId == TaskEventType.LOCKED.id())
            {
                taskEvent.setEventType(TaskEventType.LOCK_EXPIRED);
                isExpired = true;
            }

            if (!isExpired)
            {
                taskEvent.setEventType(TaskEventType.LOCK_EXPIRATION_REJECTED);
            }
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeEventToLogStream(writer);
        }

        @Override
        public void updateState()
        {
            if (isExpired)
            {
                final int lockOwner = indexAccessor.getLockOwner();

                indexWriter.write(eventKey, TaskEventType.LOCK_EXPIRED, lockOwner, -1);
            }
        }
    }

    class UpdateRetriesTaskProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            indexAccessor.wrapIndexKey(eventKey);
            final int typeId = indexAccessor.getTypeId();

            if (typeId == TaskEventType.FAILED.id())
            {
                taskEvent.setEventType(TaskEventType.RETRIES_UPDATED);
            }
            else
            {
                taskEvent.setEventType(TaskEventType.UPDATE_RETRIES_REJECTED);
            }
        }

        @Override
        public boolean executeSideEffects()
        {
            return writeResponse();
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writeEventToLogStream(writer);
        }
    }

    class IndexAccessor
    {
        static final int MISSING_VALUE = -1;

        protected final UnsafeBuffer indexValueReadBuffer = new UnsafeBuffer(new byte[INDEX_VALUE_LENGTH]);

        protected boolean isRead = false;

        void wrapIndexKey(long key)
        {
            isRead = false;

            final byte[] indexValue = taskIndex.get(key);
            if (indexValue != null)
            {
                indexValueReadBuffer.wrap(indexValue);

                isRead = true;
            }
        }

        public int getLockOwner()
        {
            int lockOwner = MISSING_VALUE;

            if (isRead)
            {
                lockOwner = indexValueReadBuffer.getInt(INDEX_LOCK_OWNER_OFFSET, ByteOrder.LITTLE_ENDIAN);
            }
            return lockOwner;
        }

        public int getTypeId()
        {
            int typeId = MISSING_VALUE;

            if (isRead)
            {
                typeId = indexValueReadBuffer.getInt(INDEX_STATE_OFFSET, ByteOrder.LITTLE_ENDIAN);
            }
            return typeId;
        }

    }

    class IndexWriter
    {
        protected final UnsafeBuffer indexValueWriteBuffer = new UnsafeBuffer(new byte[INDEX_VALUE_LENGTH]);

        protected void write(long eventKey, TaskEventType eventType, int lockOwner, long position)
        {
            indexValueWriteBuffer.putInt(INDEX_STATE_OFFSET, eventType.id(), ByteOrder.LITTLE_ENDIAN);
            indexValueWriteBuffer.putInt(INDEX_LOCK_OWNER_OFFSET, lockOwner, ByteOrder.LITTLE_ENDIAN);

            taskIndex.put(eventKey, indexValueWriteBuffer.byteArray());
        }
    }

}

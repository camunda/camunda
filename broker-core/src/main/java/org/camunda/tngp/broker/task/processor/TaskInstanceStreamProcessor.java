package org.camunda.tngp.broker.task.processor;

import static org.camunda.tngp.broker.util.payload.PayloadUtil.isNilPayload;
import static org.camunda.tngp.broker.util.payload.PayloadUtil.isValidPayload;
import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.task.CreditsRequest;
import org.camunda.tngp.broker.task.TaskSubscriptionManager;
import org.camunda.tngp.broker.task.data.TaskEvent;
import org.camunda.tngp.broker.task.data.TaskEventType;
import org.camunda.tngp.broker.task.index.TaskInstanceIndex;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.util.buffer.BufferUtil;
import org.camunda.tngp.util.time.ClockUtil;

public class TaskInstanceStreamProcessor implements StreamProcessor
{
    protected static final short STATE_CREATED = 1;
    protected static final short STATE_LOCKED = 2;
    protected static final short STATE_FAILED = 3;
    protected static final short STATE_LOCK_EXPIRED = 4;

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
    protected final CancelTaskProcessor cancelTaskProcessor = new CancelTaskProcessor();

    protected final TaskInstanceIndex taskIndex;

    protected final TaskEvent taskEvent = new TaskEvent();
    protected final CreditsRequest creditsRequest = new CreditsRequest();

    protected DirectBuffer logStreamTopicName;
    protected int logStreamPartitionId;

    protected LogStream targetStream;

    protected long eventPosition = 0;
    protected long eventKey = 0;
    protected long sourceEventPosition = 0;

    public TaskInstanceStreamProcessor(CommandResponseWriter responseWriter, SubscribedEventWriter subscribedEventWriter, IndexStore indexStore, TaskSubscriptionManager taskSubscriptionManager)
    {
        this.responseWriter = responseWriter;
        this.subscribedEventWriter = subscribedEventWriter;
        this.indexStore = indexStore;
        this.taskSubscriptionManager = taskSubscriptionManager;

        taskIndex = new TaskInstanceIndex(indexStore);
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return taskIndex.getSnapshotSupport();
    }

    public void onOpen(StreamProcessorContext context)
    {
        final LogStream sourceStream = context.getSourceStream();
        logStreamTopicName = sourceStream.getTopicName();
        logStreamPartitionId = sourceStream.getPartitionId();

        targetStream = context.getTargetStream();
    }

    public static MetadataFilter eventFilter()
    {
        return (m) -> m.getEventType() == EventType.TASK_EVENT;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        taskIndex.reset();

        eventPosition = event.getPosition();
        eventKey = event.getKey();
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
            case CANCEL:
                eventProcessor = cancelTaskProcessor;
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
            .topicName(logStreamTopicName)
            .partitionId(logStreamPartitionId)
            .key(eventKey)
            .eventWriter(taskEvent)
            .tryWriteResponse();
    }

    protected long writeEventToLogStream(LogStreamWriter writer)
    {
        targetEventMetadata.reset();
        targetEventMetadata
            .protocolVersion(Constants.PROTOCOL_VERSION)
            .eventType(TASK_EVENT)
            .raftTermId(targetStream.getTerm());

        return writer
            .key(eventKey)
            .metadataWriter(targetEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
    }

    private class CreateTaskProcessor implements EventProcessor
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
            taskIndex
                .newTaskInstance(eventKey)
                .setState(STATE_CREATED)
                .write();
        }
    }

    private class LockTaskProcessor implements EventProcessor
    {
        protected boolean isLocked;

        @Override
        public void processEvent()
        {
            isLocked = false;

            final short state = taskIndex.wrapTaskInstanceKey(eventKey).getState();

            final boolean isLockable = state == STATE_CREATED || state == STATE_FAILED || state == STATE_LOCK_EXPIRED;

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
                        .topicName(logStreamTopicName)
                        .partitionId(logStreamPartitionId)
                        .key(eventKey)
                        .subscriberKey(sourceEventMetadata.getSubscriberKey())
                        .subscriptionType(SubscriptionType.TASK_SUBSCRIPTION)
                        .eventType(TASK_EVENT)
                        .eventWriter(taskEvent)
                        .tryWriteMessage();
            }
            else
            {
                final long subscriptionId = sourceEventMetadata.getSubscriberKey();

                creditsRequest.setSubscriberKey(subscriptionId);
                creditsRequest.setCredits(1);
                success = taskSubscriptionManager.increaseSubscriptionCreditsAsync(creditsRequest);
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
            if (isLocked)
            {
                taskIndex
                    .setState(STATE_LOCKED)
                    .setLockOwner(taskEvent.getLockOwner())
                    .write();
            }
        }
    }

    private class CompleteTaskProcessor implements EventProcessor
    {
        protected boolean isCompleted;

        @Override
        public void processEvent()
        {
            isCompleted = false;

            taskIndex.wrapTaskInstanceKey(eventKey);
            final short state = taskIndex.getState();

            TaskEventType taskEventType = TaskEventType.COMPLETE_REJECTED;

            final boolean isCompletable = state == STATE_LOCKED || state == STATE_LOCK_EXPIRED;
            if (isCompletable)
            {
                final DirectBuffer payload = taskEvent.getPayload();
                if (isNilPayload(payload) || isValidPayload(payload))
                {
                    if (BufferUtil.contentsEqual(taskIndex.getLockOwner(), taskEvent.getLockOwner()))
                    {
                        taskEventType = TaskEventType.COMPLETED;
                        isCompleted = true;
                    }
                }
            }

            taskEvent.setEventType(taskEventType);
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
                taskIndex.remove(eventKey);
            }
        }
    }

    private class FailTaskProcessor implements EventProcessor
    {
        protected boolean isFailed;

        @Override
        public void processEvent()
        {
            isFailed = false;

            taskIndex.wrapTaskInstanceKey(eventKey);
            if (taskIndex.getState() == STATE_LOCKED && BufferUtil.contentsEqual(taskIndex.getLockOwner(), taskEvent.getLockOwner()))
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
                taskIndex
                    .setState(STATE_FAILED)
                    .write();
            }
        }
    }

    private class ExpireLockTaskProcessor implements EventProcessor
    {
        protected boolean isExpired;

        @Override
        public void processEvent()
        {
            isExpired = false;

            taskIndex.wrapTaskInstanceKey(eventKey);
            if (taskIndex.getState() == STATE_LOCKED)
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
                taskIndex
                    .setState(STATE_LOCK_EXPIRED)
                    .write();
            }
        }
    }

    private class UpdateRetriesTaskProcessor implements EventProcessor
    {
        @Override
        public void processEvent()
        {
            final short state = taskIndex.wrapTaskInstanceKey(eventKey).getState();

            if (state == STATE_FAILED && taskEvent.getRetries() > 0)
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

    private class CancelTaskProcessor implements EventProcessor
    {
        private boolean isCanceled;

        @Override
        public void processEvent()
        {
            isCanceled = false;

            final short state = taskIndex.wrapTaskInstanceKey(eventKey).getState();

            if (state > 0)
            {
                taskEvent.setEventType(TaskEventType.CANCELED);
                isCanceled = true;
            }
            else
            {
                taskEvent.setEventType(TaskEventType.CANCEL_REJECTED);
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
            if (isCanceled)
            {
                taskIndex.remove(eventKey);
            }
        }
    }
}

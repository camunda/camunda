package org.camunda.tngp.broker.taskqueue.processor;

import static org.agrona.BitUtil.SIZE_OF_BYTE;

import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.HashIndexSnapshotSupport;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.hashindex.Long2BytesHashIndex;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;

public class TaskInstanceStreamProcessor implements StreamProcessor
{
    protected final BrokerEventMetadata sourceEventMetadata = new BrokerEventMetadata();
    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

    protected final CreateTaskProcessor createTaskProcessor = new CreateTaskProcessor();

    protected CommandResponseWriter responseWriter;

    // TODO inject index store
    protected final IndexStore indexStore = FileChannelIndexStore.tempFileIndexStore();
    protected Long2BytesHashIndex taskIndex = new Long2BytesHashIndex(indexStore, 100, 1, 2 * SIZE_OF_BYTE);

    protected final HashIndexSnapshotSupport<Long2BytesHashIndex> indexSnapshotSupport = new HashIndexSnapshotSupport<>(taskIndex, indexStore);

    protected int streamId;

    protected long eventPosition = 0;
    protected long eventKey = 0;

    protected final TaskEvent taskEvent = new TaskEvent();

    public TaskInstanceStreamProcessor(CommandResponseWriter responseWriter)
    {
        this.responseWriter = responseWriter;
    }

    public void onOpen(StreamProcessorContext context)
    {
        streamId = context.getSourceStream().getId();
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
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
                .sourceEvent(streamId, eventPosition)
                .metadataWriter(targetEventMetadata)
                .valueWriter(taskEvent)
                .tryWrite();
        }

        @Override
        public void updateState()
        {
            // TODO update state
            //taskIndex.put(eventKey, eventPosition);
        }

    }

}

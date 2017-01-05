package org.camunda.tngp.broker.taskqueue.processor;

import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
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

    protected CmdResponseWriter responseWriter;

    // TODO inject index
    protected final Long2LongHashIndex taskIndex = null;
    protected int streamId;

    protected long eventPosition = 0;
    protected long eventKey = 0;

    protected final TaskEvent taskEvent = new TaskEvent();

    public TaskInstanceStreamProcessor(CmdResponseWriter responseWriter)
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

package org.camunda.tngp.broker.taskqueue.processor;

import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.protocol.clientapi.TaskEventType;

public class TaskInstanceStreamProcessor implements StreamProcessor
{
    protected final TaskEvent taskEvent = new TaskEvent();

    protected final BrokerEventMetadata sourceEventMetadata = new BrokerEventMetadata();
    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

    protected final CreateTaskProcessor createTaskProcessor = new CreateTaskProcessor();

    protected final CmdResponseWriter responseWriter = null;
    protected final Long2LongHashIndex taskIndex = null;
    protected int streamId;

    protected long eventPosition = 0;
    protected long eventKey = 0;

    public void onOpen(StreamProcessorContext context)
    {
        streamId = context.getSourceStream().getId();
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        eventPosition = event.getPosition();
        eventKey = event.getLongKey();

        taskEvent.decode(event.getValueBuffer(), event.getValueOffset());
        sourceEventMetadata.wrap(event.getMetadata(), event.getMetadataOffset(), event.getMetadataLength());

        final TaskEventType evtType = taskEvent.getEvtType();

        EventProcessor eventProcessor = null;

        if (evtType != null)
        {
            switch (evtType)
            {
                case CREATE:
                    eventProcessor = createTaskProcessor;
                    break;

                default:
                    break;
            }
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
            taskEvent.setEvtType(TaskEventType.CREATED);
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
            taskIndex.put(eventKey, eventPosition);
        }

    }

}

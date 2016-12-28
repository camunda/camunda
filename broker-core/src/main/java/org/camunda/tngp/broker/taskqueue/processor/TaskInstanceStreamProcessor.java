package org.camunda.tngp.broker.taskqueue.processor;

import org.camunda.tngp.broker.logstreams.requests.LogStreamRequest;
import org.camunda.tngp.broker.logstreams.requests.LogStreamRequestManager;
import org.camunda.tngp.broker.taskqueue.processor.stuff.EncodedStuff;
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

    protected final CreateTaskProcessor createTaskProcessor = new CreateTaskProcessor();

    protected final CmdResponseWriter responseWriter = null;
    protected final LogStreamRequestManager requestQueue = null;
    protected final Long2LongHashIndex taskIndex = null;
    protected int streamId;

    protected EncodedStuff encodedEvent = new EncodedStuff();

    protected long eventPosition = 0;
    protected long eventKey = 0;

    @Override
    public void onOpen(StreamProcessorContext streamProcessorContext)
    {
        streamId = streamProcessorContext.getSourceStream().getId();
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        eventPosition = event.getPosition();
        eventKey = event.getLongKey();

        taskEvent.decode(event.getValueBuffer(), event.getValueOffset());

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

            encodedEvent.encode(taskEvent);
        }

        @Override
        public boolean executeSideEffects()
        {
            final LogStreamRequest request = requestQueue.poll(eventPosition);

            boolean success = true;

            if (request != null)
            {
                success = responseWriter.forRequest(request)
                    .topicId(streamId)
                    .longKey(eventKey)
                    .event(encodedEvent.getBuffer(), 0, encodedEvent.getEncodedLength())
                    .tryWriteResponse();
            }

            return success;
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            return writer.key(eventKey)
                .value(encodedEvent.getBuffer(), 0, encodedEvent.getEncodedLength())
                .tryWrite();
        }

        @Override
        public void updateState()
        {
            taskIndex.put(eventKey, eventPosition);
        }

    }

}

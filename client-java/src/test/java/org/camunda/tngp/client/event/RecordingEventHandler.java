package org.camunda.tngp.client.event;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

public class RecordingEventHandler implements TopicEventHandler
{

    protected List<RecordedEvent> events = new ArrayList<>();
    protected ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public void handle(EventMetadata metadata, TopicEvent event)
    {
        final RecordedEvent recordedEvent = new RecordedEvent();
        recordedEvent.metadata = metadata;
        recordedEvent.event = event;
        this.events.add(recordedEvent);
    }

    public int numRecordedEvents()
    {
        return events.size();
    }

    public List<RecordedEvent> getRecordedEvents()
    {
        return events;
    }

    public void reset()
    {
        this.events.clear();
    }

    public static class RecordedEvent
    {
        protected EventMetadata metadata;
        protected TopicEvent event;

        public EventMetadata getMetadata()
        {
            return metadata;
        }
        public TopicEvent getEvent()
        {
            return event;
        }
    }
}

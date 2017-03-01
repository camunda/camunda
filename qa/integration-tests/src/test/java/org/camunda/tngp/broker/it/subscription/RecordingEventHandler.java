package org.camunda.tngp.broker.it.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.camunda.tngp.client.event.EventMetadata;
import org.camunda.tngp.client.event.TopicEvent;
import org.camunda.tngp.client.event.TopicEventHandler;
import org.camunda.tngp.client.event.TopicEventType;

import com.fasterxml.jackson.databind.JsonNode;
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

    public int numRecordedEventsOfType(TopicEventType type)
    {
        return (int) events.stream().filter((re) -> re.metadata.getEventType() == type).count();
    }

    public int numRecordedTaskEvents()
    {
        return numRecordedEventsOfType(TopicEventType.TASK);
    }

    public int numRecordedRaftEvents()
    {
        return numRecordedEventsOfType(TopicEventType.RAFT);
    }

    public List<RecordedEvent> getRecordedEvents()
    {
        return events;
    }

    public void assertTaskEvent(int index, long taskKey, String eventType) throws IOException
    {
        final List<RecordedEvent> taskEvents = events.stream()
                .filter((re) -> re.metadata.getEventType() == TopicEventType.TASK)
                .collect(Collectors.toList());

        final RecordedEvent taskEvent = taskEvents.get(index);

        final EventMetadata eventMetadata = taskEvent.metadata;
        assertThat(eventMetadata.getEventType()).isEqualTo(TopicEventType.TASK);
        assertThat(eventMetadata.getEventKey()).isEqualTo(taskKey);

        final JsonNode event = objectMapper.readTree(taskEvent.event.getJson());
        assertThat(event.get("event").asText()).isEqualTo(eventType);
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

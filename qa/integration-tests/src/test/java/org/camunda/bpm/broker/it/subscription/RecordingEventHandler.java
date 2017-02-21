package org.camunda.bpm.broker.it.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.client.event.EventMetadata;
import org.camunda.tngp.client.event.TopicEvent;
import org.camunda.tngp.client.event.TopicEventHandler;
import org.camunda.tngp.client.event.TopicEventType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RecordingEventHandler implements TopicEventHandler
{

    protected List<EventMetadata> metadata = new ArrayList<>();
    protected List<TopicEvent> events = new ArrayList<>();
    protected ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public void handle(EventMetadata metadata, TopicEvent event)
    {
        this.metadata.add(metadata);
        this.events.add(event);
    }

    public EventMetadata getMetadata(int eventIndex)
    {
        return metadata.get(eventIndex);
    }

    public TopicEvent getEvent(int eventIndex)
    {
        return events.get(eventIndex);
    }

    public int numRecordedEvents()
    {
        return events.size();
    }

    public void assertTaskEvent(int index, long taskKey, String eventType) throws IOException
    {

        final EventMetadata eventMetadata = getMetadata(index);
        assertThat(eventMetadata.getEventType()).isEqualTo(TopicEventType.TASK);
        assertThat(eventMetadata.getEventKey()).isEqualTo(taskKey);

        final JsonNode event = objectMapper.readTree(getEvent(index).getJson());
        assertThat(event.get("event").asText()).isEqualTo(eventType);
    }
}

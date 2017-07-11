/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.it.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.event.EventMetadata;
import io.zeebe.client.event.TopicEvent;
import io.zeebe.client.event.TopicEventHandler;
import io.zeebe.client.event.TopicEventType;

public class RecordingEventHandler implements TopicEventHandler
{

    protected List<RecordedEvent> events = new CopyOnWriteArrayList<>();
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
        assertThat(event.get("eventType").asText()).isEqualTo(eventType);
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

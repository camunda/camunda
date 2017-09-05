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
import io.zeebe.client.event.GeneralEvent;
import io.zeebe.client.event.UniversalEventHandler;
import io.zeebe.client.event.TopicEventType;

public class RecordingEventHandler implements UniversalEventHandler
{

    protected List<GeneralEvent> events = new CopyOnWriteArrayList<>();
    protected ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public void handle(GeneralEvent event)
    {
        this.events.add(event);
    }

    public int numRecordedEvents()
    {
        return events.size();
    }

    public int numRecordedEventsOfType(TopicEventType type)
    {
        return (int) events.stream().filter(e -> e.getMetadata().getType() == type).count();
    }

    public int numRecordedTaskEvents()
    {
        return numRecordedEventsOfType(TopicEventType.TASK);
    }

    public int numRecordedRaftEvents()
    {
        return numRecordedEventsOfType(TopicEventType.RAFT);
    }

    public List<GeneralEvent> getRecordedEvents()
    {
        return events;
    }

    public void assertTaskEvent(int index, long taskKey, String eventType) throws IOException
    {
        final List<GeneralEvent> taskEvents = events.stream()
                .filter(e -> e.getMetadata().getType() == TopicEventType.TASK)
                .collect(Collectors.toList());

        final GeneralEvent taskEvent = taskEvents.get(index);

        final EventMetadata eventMetadata = taskEvent.getMetadata();
        assertThat(eventMetadata.getType()).isEqualTo(TopicEventType.TASK);
        assertThat(eventMetadata.getKey()).isEqualTo(taskKey);

        final JsonNode event = objectMapper.readTree(taskEvent.getJson());
        assertThat(event.get("state").asText()).isEqualTo(eventType);
    }

    public void reset()
    {
        this.events.clear();
    }

    public static class RecordedEvent
    {
        protected EventMetadata metadata;
        protected GeneralEvent event;

        public EventMetadata getMetadata()
        {
            return metadata;
        }
        public GeneralEvent getEvent()
        {
            return event;
        }
    }
}

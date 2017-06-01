/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.it.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.camunda.tngp.client.event.EventMetadata;
import org.camunda.tngp.client.event.TopicEvent;
import org.camunda.tngp.client.event.TopicEventHandler;
import org.camunda.tngp.client.event.TopicEventType;
import org.camunda.tngp.client.workflow.impl.WorkflowInstanceEventType;

public class WorkflowInstanceEventRecorder implements TopicEventHandler
{
    private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile("\"eventType\":\"(\\w+)\"");

    private final List<WorkflowEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void handle(EventMetadata metadata, TopicEvent event) throws Exception
    {
        if (metadata.getEventType() == TopicEventType.WORKFLOW)
        {
            final WorkflowEvent workflowEvent = new WorkflowEvent();

            workflowEvent.key = metadata.getEventKey();

            final Matcher matcher = EVENT_TYPE_PATTERN.matcher(event.getJson());
            if (matcher.find())
            {
                final String eventType = matcher.group(1);
                workflowEvent.eventType = eventType;
            }

            events.add(workflowEvent);
        }
    }

    public List<String> getEventTypes()
    {
        return events.stream().map(e -> e.eventType).collect(Collectors.toList());
    }

    public boolean hasWorkflowEvent(final Predicate<WorkflowEvent> matcher)
    {
        return events.stream().anyMatch(matcher);
    }

    public List<WorkflowEvent> getWorkflowEvents(final Predicate<WorkflowEvent> matcher)
    {
        return events.stream().filter(matcher).collect(Collectors.toList());
    }

    public WorkflowEvent getSingleWorkflowEvent(final Predicate<WorkflowEvent> matcher)
    {
        return events.stream().filter(matcher).findFirst().orElseThrow(() -> new AssertionError("no event found"));
    }

    public static Predicate<WorkflowEvent> eventType(final WorkflowInstanceEventType type)
    {
        return event -> event.eventType.equals(type.name());
    }

    public static class WorkflowEvent
    {
        private String eventType;
        private long key;

        public String getEventType()
        {
            return eventType;
        }

        public long getKey()
        {
            return key;
        }
    }

}

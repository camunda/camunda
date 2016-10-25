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
package org.camunda.tngp.client.event.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.camunda.tngp.client.event.Event;
import org.camunda.tngp.client.event.EventsBatch;
import org.camunda.tngp.client.event.TaskInstanceEvent;
import org.camunda.tngp.client.event.WorkflowDefinitionEvent;

public class EventsBatchImpl implements EventsBatch
{
    protected final List<Event> events = new ArrayList<>();

    @Override
    public List<Event> getEvents()
    {
        return events;
    }

    @Override
    public List<TaskInstanceEvent> getTaskInstanceEvents()
    {
        return getEventsOfType(TaskInstanceEvent.class);
    }

    @Override
    public List<WorkflowDefinitionEvent> getWorkflowDefinitionEvents()
    {
        return getEventsOfType(WorkflowDefinitionEvent.class);
    }

    protected <T extends Event> List<T> getEventsOfType(Class<T> type)
    {
        return events.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .collect(Collectors.toList());
    }

    public void addEvent(Event event)
    {
        events.add(event);
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("EventsBatchImpl [events=");
        builder.append(events);
        builder.append("]");
        return builder.toString();
    }

}

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.camunda.tngp.client.event.EventMetadata;
import org.camunda.tngp.client.event.TopicEvent;
import org.camunda.tngp.client.event.TopicEventHandler;
import org.camunda.tngp.client.event.TopicEventType;

public class WorkflowInstanceEventRecorder implements TopicEventHandler
{
    private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile("\"eventType\":\"(\\w+)\"");

    private final List<String> eventTypes = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void handle(EventMetadata metadata, TopicEvent event) throws Exception
    {
        if (metadata.getEventType() == TopicEventType.WORKFLOW)
        {
            final Matcher matcher = EVENT_TYPE_PATTERN.matcher(event.getJson());
            if (matcher.find())
            {
                final String eventType = matcher.group(1);
                eventTypes.add(eventType);
            }
        }
    }

    public List<String> getEventTypes()
    {
        return Collections.unmodifiableList(eventTypes);
    }

}

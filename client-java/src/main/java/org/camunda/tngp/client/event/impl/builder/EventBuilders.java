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
package org.camunda.tngp.client.event.impl.builder;

import org.agrona.collections.Int2ObjectHashMap;
import org.camunda.tngp.protocol.log.TaskInstanceEncoder;
import org.camunda.tngp.protocol.log.WfDefinitionEncoder;
import org.camunda.tngp.protocol.log.WfDefinitionRequestEncoder;

public class EventBuilders
{
    protected static final Int2ObjectHashMap<EventBuilder> EVENT_BUILDERS = new Int2ObjectHashMap<>();

    protected static final EventBuilder UNKNOWN_EVENT_BUILDER = new UnknownEventBuilder();

    protected static final EventBuilder TASK_INSTANCE_EVENT_BUILDER =
            registerEventBuilder(TaskInstanceEncoder.TEMPLATE_ID, new TaskInstanceEventBuilder());

    protected static final EventBuilder WORKFLOW_DEFINITION_EVENT_BUILDER =
            registerEventBuilder(WfDefinitionEncoder.TEMPLATE_ID, new WorkflowDefinitionEventBuilder());

    protected static final EventBuilder WORKFLOW_DEFINITION_REQUEST_EVENT_BUILDER =
            registerEventBuilder(WfDefinitionRequestEncoder.TEMPLATE_ID, new WorkflowDefinitionRequestEventBuilder());

    protected static EventBuilder registerEventBuilder(int templateId, EventBuilder eventBuilder)
    {
        if (EVENT_BUILDERS.containsKey(templateId))
        {
            throw new RuntimeException("cannot register two event builder for the same template id");
        }

        EVENT_BUILDERS.put(templateId, eventBuilder);

        return eventBuilder;
    }

    public static EventBuilder getEventBuilder(int templateId)
    {
        EventBuilder eventBuilder = UNKNOWN_EVENT_BUILDER;

        if (EVENT_BUILDERS.containsKey(templateId))
        {
            eventBuilder = EVENT_BUILDERS.get(templateId);
        }

        return eventBuilder;
    }

}

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

import static org.camunda.tngp.util.buffer.BufferUtil.bufferAsString;

import org.agrona.DirectBuffer;
import org.camunda.tngp.client.event.Event;
import org.camunda.tngp.client.event.impl.dto.WorkflowDefinitionEventImpl;
import org.camunda.tngp.protocol.wf.WfDefinitionReader;

public class WorkflowDefinitionEventBuilder implements EventBuilder
{
    protected final WfDefinitionReader reader = new WfDefinitionReader();

    @Override
    public Event build(long position, DirectBuffer buffer)
    {
        reader.wrap(buffer, 0, buffer.capacity());

        final WorkflowDefinitionEventImpl event = new WorkflowDefinitionEventImpl();

        event.setPosition(position);
        event.setRawBuffer(buffer);

        event.setId(reader.id());
        event.setKey(bufferAsString(reader.getTypeKey()));

        event.setResource(bufferAsString(reader.getResource()));

        return event;
    }

}

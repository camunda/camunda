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

import org.agrona.DirectBuffer;
import org.camunda.tngp.client.event.Event;
import org.camunda.tngp.client.event.EventsBatch;
import org.camunda.tngp.client.event.impl.builder.EventBuilder;
import org.camunda.tngp.client.event.impl.builder.EventBuilders;
import org.camunda.tngp.client.impl.cmd.ClientResponseHandler;
import org.camunda.tngp.protocol.event.EventBatchDecoder;
import org.camunda.tngp.protocol.event.EventBatchReader;
import org.camunda.tngp.protocol.log.MessageHeaderDecoder;

public class EventBatchHandler implements ClientResponseHandler<EventsBatch>
{
    protected final EventBatchReader reader = new EventBatchReader();

    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

    @Override
    public int getResponseSchemaId()
    {
        return EventBatchDecoder.SCHEMA_ID;
    }

    @Override
    public int getResponseTemplateId()
    {
        return EventBatchDecoder.TEMPLATE_ID;
    }

    @Override
    public EventsBatch readResponse(DirectBuffer responseBuffer, int offset, int length)
    {
        reader.wrap(responseBuffer, offset, length);

        final EventsBatchImpl batch = new EventsBatchImpl();

        for (int i = 0; i < reader.eventCount(); i++)
        {
            reader.nextEvent();

            final long position = reader.currentPosition();
            final DirectBuffer eventBuffer = reader.currentEvent();

            final Event event = createEvent(position, eventBuffer);
            batch.addEvent(event);
        }

        return batch;
    }

    protected Event createEvent(final long position, final DirectBuffer eventBuffer)
    {
        messageHeaderDecoder.wrap(eventBuffer, 0);

        final int templateId = messageHeaderDecoder.templateId();
        final EventBuilder eventBuilder = EventBuilders.getEventBuilder(templateId);

        return eventBuilder.build(position, eventBuffer);
    }

}

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
package org.camunda.tngp.broker.event.request.handler;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.event.EventContext;
import org.camunda.tngp.broker.event.EventErrors;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.event.EventBatchWriter;
import org.camunda.tngp.protocol.event.PollEventsDecoder;
import org.camunda.tngp.protocol.event.PollEventsEncoder;
import org.camunda.tngp.protocol.event.PollEventsRequestReader;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

public class PollEventsRequestHandler implements BrokerRequestHandler<EventContext>
{
    public static final int EVENT_BUFFER_SIZE = 1024 * 1024;

    protected final  PollEventsRequestReader requestReader;
    protected final EventBatchWriter batchWriter;

    protected final ErrorWriter errorWriter;

    protected final EventFinder eventFinder;

    public PollEventsRequestHandler()
    {
        this(new PollEventsRequestReader(), new EventFinder(EVENT_BUFFER_SIZE), new EventBatchWriter(EVENT_BUFFER_SIZE), new ErrorWriter());
    }

    public PollEventsRequestHandler(PollEventsRequestReader requestReader, EventFinder eventFinder, EventBatchWriter batchWriter, ErrorWriter errorWriter)
    {
        this.requestReader = requestReader;
        this.eventFinder = eventFinder;
        this.batchWriter = batchWriter;
        this.errorWriter = errorWriter;
    }

    @Override
    public long onRequest(EventContext context, DirectBuffer msg, int offset, int length, DeferredResponse response)
    {
        requestReader.wrap(msg, offset, length);

        final long startPosition = requestReader.startPosition();
        if (startPosition == PollEventsEncoder.startPositionNullValue() || startPosition < 0)
        {
            return writeError(response, "start position must be greater or equal to 0");
        }

        final int maxEvents = requestReader.maxEvents();
        if (maxEvents == PollEventsEncoder.maxEventsNullValue() || maxEvents <= 0)
        {
            return writeError(response, "max events must be greater than 0");
        }

        final int topicId = requestReader.topicId();
        if (topicId == PollEventsEncoder.topicIdNullValue() || topicId < 0)
        {
            return writeError(response, "topic id must be greater or equal to 0");
        }

        final Log log = context.getLogManager().getLogById(topicId);
        if (log == null)
        {
            return writeError(response, "found no topic with id: " + topicId);
        }

        if (startPosition < log.getLastPosition())
        {
            eventFinder.init(log, startPosition, maxEvents);

            final int eventCount = eventFinder.findEvents();
            final DirectBuffer eventBuffer = eventFinder.getEventBuffer();

            for (int i = 0; i < eventCount; i++)
            {
                final long eventPosition = eventFinder.getEventPosition(i);
                final int eventOffset = eventFinder.getEventBufferOffset(i);
                final int eventLength = eventFinder.getEventBufferLength(i);

                batchWriter.appendEvent(eventPosition, eventBuffer, eventOffset, eventLength);
            }
        }

        return writeResponse(response);
    }

    protected int writeResponse(DeferredResponse response)
    {
        int result = -1;

        if (response.allocateAndWrite(batchWriter))
        {
            response.commit();
            result = 0;
        }
        return result;
    }

    protected int writeError(DeferredResponse response, String errorMessage)
    {
        int result = -1;

        errorWriter
            .componentCode(EventErrors.COMPONENT_CODE)
            .detailCode(EventErrors.POLL_EVENTS_ERROR)
            .errorMessage(errorMessage);

        if (response.allocateAndWrite(errorWriter))
        {
            response.commit();
            result = 0;
        }

        return result;
    }

    @Override
    public int getTemplateId()
    {
        return PollEventsDecoder.TEMPLATE_ID;
    }

}

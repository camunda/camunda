/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.orchestration.state;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.slf4j.Logger;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.orchestration.topic.TopicEvent;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.util.buffer.BufferUtil;

public class TopicCreatedProcessor implements TypedRecordProcessor<TopicEvent>
{
    private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final Predicate<DirectBuffer> topicAlreadyCreated;
    private final Consumer<DirectBuffer> notifyListeners;
    private final BiConsumer<Long, TopicEvent> updateTopicState;

    private boolean isCreated;

    public TopicCreatedProcessor(final Predicate<DirectBuffer> topicAlreadyCreated, final Consumer<DirectBuffer> notifyListeners, final BiConsumer<Long, TopicEvent> updateTopicState)
    {
        this.topicAlreadyCreated = topicAlreadyCreated;
        this.notifyListeners = notifyListeners;
        this.updateTopicState = updateTopicState;
    }

    @Override
    public void processRecord(final TypedRecord<TopicEvent> event)
    {
        final TopicEvent topicEvent = event.getValue();

        final DirectBuffer topicName = topicEvent.getName();

        isCreated = !topicAlreadyCreated.test(topicName);

        if (!isCreated)
        {
            LOG.warn("Rejecting topic create complete as topic {} was already created", BufferUtil.bufferAsString(topicName));
        }
    }

    @Override
    public boolean executeSideEffects(final TypedRecord<TopicEvent> event, final TypedResponseWriter responseWriter)
    {
        if (isCreated)
        {
            notifyListeners.accept(event.getValue().getName());
        }

        return true;
    }

    @Override
    public long writeRecord(final TypedRecord<TopicEvent> event, final TypedStreamWriter writer)
    {
        if (isCreated)
        {
            return writer.writeFollowUpEvent(event.getKey(), Intent.CREATED, event.getValue());
        }
        else
        {
            return writer.writeRejection(event);
        }
    }

    @Override
    public void updateState(final TypedRecord<TopicEvent> event)
    {
        if (isCreated)
        {
            updateTopicState.accept(event.getKey(), event.getValue());
        }
    }
}

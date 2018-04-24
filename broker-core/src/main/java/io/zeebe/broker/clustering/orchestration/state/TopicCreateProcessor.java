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

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.orchestration.topic.TopicEvent;
import io.zeebe.broker.clustering.orchestration.topic.TopicState;
import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedEventProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class TopicCreateProcessor implements TypedEventProcessor<TopicEvent>
{
    private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final Predicate<DirectBuffer> topicExists;
    private final Consumer<DirectBuffer> notifyListeners;
    private final BiConsumer<Long, TopicEvent> addTopic;

    private boolean isCreating;

    public TopicCreateProcessor(final Predicate<DirectBuffer> topicExists, final Consumer<DirectBuffer> notifyListeners, final BiConsumer<Long, TopicEvent> addTopic)
    {
        this.topicExists = topicExists;
        this.notifyListeners = notifyListeners;
        this.addTopic = addTopic;
    }

    @Override
    public void processEvent(final TypedEvent<TopicEvent> event)
    {
        isCreating = false;

        final TopicEvent topicEvent = event.getValue();
        final DirectBuffer topicName = topicEvent.getName();

        if (topicExists.test(topicName))
        {
            LOG.warn("Rejecting topic {} creation as a topic with the same name already exists", bufferAsString(topicName));
            topicEvent.setState(TopicState.CREATE_REJECTED);
        }
        else if (topicEvent.getPartitions() < 1)
        {
            LOG.warn("Rejecting topic {} creation as a topic has to have at least one partition", bufferAsString(topicName));
            topicEvent.setState(TopicState.CREATE_REJECTED);
        }
        else if (topicEvent.getReplicationFactor() < 1)
        {
            LOG.warn("Rejecting topic {} creation as a topic has to have at least one replication", bufferAsString(topicName));
            topicEvent.setState(TopicState.CREATE_REJECTED);
        }
        else
        {
            LOG.info("Creating topic {} with partition count {} and replication factor {}", bufferAsString(topicName), topicEvent.getPartitions(), topicEvent.getReplicationFactor());
            isCreating = true;
            topicEvent.setState(TopicState.CREATING);
        }
    }

    @Override
    public boolean executeSideEffects(final TypedEvent<TopicEvent> event, final TypedResponseWriter responseWriter)
    {
        final boolean written = responseWriter.write(event);

        if (written && isCreating)
        {
            notifyListeners.accept(event.getValue().getName());
        }

        return written;
    }

    @Override
    public long writeEvent(final TypedEvent<TopicEvent> event, final TypedStreamWriter writer)
    {
        return writer.writeFollowupEvent(event.getKey(), event.getValue());
    }

    @Override
    public void updateState(final TypedEvent<TopicEvent> event)
    {
        if (isCreating)
        {
            addTopic.accept(event.getKey(), event.getValue());
        }
    }
}

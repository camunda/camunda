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
package io.zeebe.gossip.protocol;

import java.util.EnumMap;
import java.util.Map;

import io.zeebe.clustering.gossip.*;
import io.zeebe.gossip.GossipContext;
import io.zeebe.transport.*;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class GossipRequestHandler implements ServerRequestHandler
{
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    private final GossipEvent gossipEvent;

    private final Logger logger;

    private final Map<GossipEventType, GossipEventConsumer> consumers = new EnumMap<>(GossipEventType.class);

    public GossipRequestHandler(GossipContext context, GossipEventFactory eventFactory)
    {
        this.logger = context.getLogger();

        this.gossipEvent = eventFactory.createFailureDetectionEvent();
    }

    public void registerGossipEventConsumer(GossipEventType eventType, GossipEventConsumer consumer)
    {
        consumers.put(eventType, consumer);
    }

    @Override
    public boolean onRequest(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length, long requestId)
    {
        headerDecoder.wrap(buffer, offset);

        final int schemaId = headerDecoder.schemaId();
        final int templateId = headerDecoder.templateId();

        if (GossipEventDecoder.SCHEMA_ID == schemaId && GossipEventDecoder.TEMPLATE_ID == templateId)
        {
            gossipEvent.wrap(buffer, offset, length);

            final GossipEventType eventType = gossipEvent.getEventType();

            final GossipEventConsumer consumer = consumers.get(eventType);
            if (consumer != null)
            {
                consumer.accept(gossipEvent, requestId, remoteAddress.getStreamId());
            }
            else
            {
                logger.warn("No consumer registered for gossip event type '{}'", eventType);
            }
        }
        else
        {
            logger.warn("Cannot handle request with schema-id '{}' and template-id '{}'", schemaId, templateId);
        }

        return true;
    }
}

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
package io.zeebe.gossip.failuredetection;

import io.zeebe.gossip.GossipContext;
import io.zeebe.gossip.Loggers;
import io.zeebe.gossip.protocol.*;
import org.slf4j.Logger;

public class PingEventHandler implements GossipEventConsumer
{
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;

    private final GossipEventSender gossipEventSender;

    public PingEventHandler(GossipContext context)
    {
        this.gossipEventSender = context.getGossipEventSender();
    }

    @Override
    public void accept(GossipEvent event, long requestId, int streamId)
    {
        final String sender = event.getSender();

        LOG.trace("Received PING from '{}'", sender);

        LOG.trace("Send ACK to '{}'", sender);

        gossipEventSender.responseAck(requestId, streamId);
    }

}

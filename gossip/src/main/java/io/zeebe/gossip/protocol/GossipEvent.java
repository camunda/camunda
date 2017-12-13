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

import java.util.Iterator;

import io.zeebe.clustering.gossip.*;
import io.zeebe.clustering.gossip.GossipEventDecoder.MembershipEventsDecoder;
import io.zeebe.clustering.gossip.GossipEventEncoder.CustomEventsEncoder;
import io.zeebe.clustering.gossip.GossipEventEncoder.MembershipEventsEncoder;
import io.zeebe.gossip.membership.GossipTerm;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class GossipEvent implements BufferReader, BufferWriter
{
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final GossipEventEncoder bodyEncoder = new GossipEventEncoder();

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final GossipEventDecoder bodyDecoder = new GossipEventDecoder();

    private final MembershipEventSupplier membershipEventSupplier;
    private final MembershipEventConsumer membershipEventConsumer;
    private final int maxMembershipEventsPerMessage;

    private final MembershipEventImpl membershipEvent = new MembershipEventImpl();

    private final SocketAddress senderAddress = new SocketAddress();
    private final SocketAddress probeMemberAddress = new SocketAddress();

    private GossipEventType eventType = GossipEventType.NULL_VAL;

    public GossipEvent(MembershipEventSupplier membershipEventSupplier, MembershipEventConsumer membershipEventConsumer, int maxMembershipEventsPerMessage)
    {
        this.membershipEventSupplier = membershipEventSupplier;
        this.membershipEventConsumer = membershipEventConsumer;
        this.maxMembershipEventsPerMessage = maxMembershipEventsPerMessage;
    }

    public GossipEventType getEventType()
    {
        return eventType;
    }

    public SocketAddress getSender()
    {
        return senderAddress;
    }

    public SocketAddress getProbeMember()
    {
        return probeMemberAddress;
    }

    public GossipEvent eventType(GossipEventType eventType)
    {
        this.eventType = eventType;
        return this;
    }

    public GossipEvent sender(SocketAddress sender)
    {
        this.senderAddress
            .port(sender.port())
            .host(sender.getHostBuffer(), 0, sender.hostLength());

        return this;
    }

    public GossipEvent probeMember(SocketAddress probeMember)
    {
        this.probeMemberAddress
            .port(probeMember.port())
            .host(probeMember.getHostBuffer(), 0, probeMember.hostLength());

        return this;
    }

    public GossipEvent reset()
    {
        this.eventType = GossipEventType.NULL_VAL;
        this.senderAddress.reset();
        this.probeMemberAddress.reset();

        return this;
    }

    @Override
    public int getLength()
    {
        int length = headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength() +
                GossipEventEncoder.senderHostHeaderLength() +
                GossipEventEncoder.probeMemberHostHeaderLength() +
                MembershipEventsEncoder.sbeHeaderSize() +
                CustomEventsEncoder.sbeHeaderSize();

        length += senderAddress.hostLength();
        length += probeMemberAddress.hostLength();

        final Iterator<MembershipEvent> membershipEvents = membershipEventSupplier.membershipEventsView(maxMembershipEventsPerMessage);
        while (membershipEvents.hasNext())
        {
            final MembershipEvent event = membershipEvents.next();

            length += event.getAddress().hostLength();
            length += MembershipEventsEncoder.sbeBlockLength() + MembershipEventsEncoder.hostHeaderLength();
        }

        return length;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        bodyEncoder.wrap(buffer, offset + headerEncoder.encodedLength());

        bodyEncoder
            .eventType(eventType)
            .senderPort(senderAddress.port())
            .probeMemberPort(probeMemberAddress.port());

        final int membershipEventSize = Math.min(membershipEventSupplier.membershipEventSize(), maxMembershipEventsPerMessage);
        final MembershipEventsEncoder membershipEventsEncoder = bodyEncoder.membershipEventsCount(membershipEventSize);

        final Iterator<MembershipEvent> membershipEvents = membershipEventSupplier.drainMembershipEvents(membershipEventSize);
        while (membershipEvents.hasNext())
        {
            final MembershipEvent membershipEvent = membershipEvents.next();
            final GossipTerm gossipTerm = membershipEvent.getGossipTerm();
            final SocketAddress address = membershipEvent.getAddress();

            membershipEventsEncoder.next()
                .eventType(membershipEvent.getType())
                .gossipEpoch(gossipTerm.getEpoch())
                .gossipHeartbeat(gossipTerm.getHeartbeat())
                .port(address.port());

            membershipEventsEncoder.putHost(address.getHostBuffer(), 0, address.hostLength());
        }

        bodyEncoder
            .putSenderHost(senderAddress.getHostBuffer(), 0, senderAddress.hostLength())
            .putProbeMemberHost(probeMemberAddress.getHostBuffer(), 0, probeMemberAddress.hostLength());
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        reset();

        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer,
                offset,
                headerDecoder.blockLength(),
                headerDecoder.version());

        eventType = bodyDecoder.eventType();

        senderAddress.port(bodyDecoder.senderPort());
        probeMemberAddress.port(bodyDecoder.probeMemberPort());

        final MembershipEventsDecoder membershipEventsDecoder = bodyDecoder.membershipEvents();
        if (membershipEventsDecoder.count() > 0)
        {
            for (MembershipEventsDecoder membershipEventDecoder : membershipEventsDecoder)
            {
                membershipEvent.type(membershipEventDecoder.eventType());

                membershipEvent.getGossipTerm()
                    .epoch(membershipEventDecoder.gossipEpoch())
                    .heartbeat(membershipEventDecoder.gossipHeartbeat());

                final SocketAddress address = membershipEvent.getAddress()
                    .port(membershipEventDecoder.port())
                    .hostLength(membershipEventDecoder.hostLength());

                membershipEventDecoder.getHost(address.getHostBuffer(), 0, address.hostLength());

                membershipEventConsumer.consumeMembershipEvent(membershipEvent);
            }
        }

        final int senderHostLength = bodyDecoder.senderHostLength();
        senderAddress.hostLength(senderHostLength);
        bodyDecoder.getSenderHost(senderAddress.getHostBuffer(), 0, senderHostLength);

        final int probeMemberHostLength = bodyDecoder.probeMemberHostLength();
        probeMemberAddress.hostLength(probeMemberHostLength);
        bodyDecoder.getProbeMemberHost(probeMemberAddress.getHostBuffer(), 0, probeMemberHostLength);
    }

}

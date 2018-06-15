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
import io.zeebe.clustering.gossip.GossipEventDecoder.CustomEventsDecoder;
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

    private final CustomEventSupplier customEventSupplier;
    private final CustomEventConsumer customEventConsumer;
    private final int maxCustomEventsPerMessage;

    private final MembershipEvent membershipEvent = new MembershipEvent();
    private final CustomEvent customEvent = new CustomEvent();

    private final SocketAddress senderAddress = new SocketAddress();
    private final SocketAddress probeMemberAddress = new SocketAddress();

    private GossipEventType eventType = GossipEventType.NULL_VAL;

    public GossipEvent(
            MembershipEventSupplier membershipEventSupplier,
            MembershipEventConsumer membershipEventConsumer,
            CustomEventSupplier customEventSupplier,
            CustomEventConsumer customEventConsumer,
            int maxMembershipEventsPerMessage,
            int maxCustomEventsPerMessage)
    {
        this.membershipEventSupplier = membershipEventSupplier;
        this.membershipEventConsumer = membershipEventConsumer;
        this.maxMembershipEventsPerMessage = maxMembershipEventsPerMessage;

        this.customEventSupplier = customEventSupplier;
        this.customEventConsumer = customEventConsumer;
        this.maxCustomEventsPerMessage = maxCustomEventsPerMessage;
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
        this.senderAddress.wrap(sender);
        return this;
    }

    public GossipEvent probeMember(SocketAddress probeMember)
    {
        this.probeMemberAddress.wrap(probeMember);
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

        final Iterator<MembershipEvent> membershipEvents = membershipEventSupplier.membershipEventViewIterator(maxMembershipEventsPerMessage);
        while (membershipEvents.hasNext())
        {
            final MembershipEvent event = membershipEvents.next();

            length +=
                    event.getAddress().hostLength() +
                    MembershipEventsEncoder.sbeBlockLength() +
                    MembershipEventsEncoder.hostHeaderLength();
        }

        final Iterator<CustomEvent> customEvents = customEventSupplier.customEventViewIterator(maxCustomEventsPerMessage);
        while (customEvents.hasNext())
        {
            final CustomEvent event = customEvents.next();

            length +=
                    event.getSenderAddress().hostLength() +
                    event.getType().capacity() +
                    event.getPayload().capacity() +
                    CustomEventsEncoder.sbeBlockLength() +
                    CustomEventsEncoder.senderHostHeaderLength() +
                    CustomEventsEncoder.eventTypeHeaderLength() +
                    CustomEventsEncoder.payloadHeaderLength();
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

        // TODO sort membership events by spread count if event count is greater that the given limit
        final int membershipEventSize = Math.min(membershipEventSupplier.membershipEventSize(), maxMembershipEventsPerMessage);
        final MembershipEventsEncoder membershipEventsEncoder = bodyEncoder.membershipEventsCount(membershipEventSize);

        final Iterator<MembershipEvent> membershipEvents = membershipEventSupplier.membershipEventDrainIterator(membershipEventSize);
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

        // TODO sort custom events by spread count if event count is greater that the given limit
        final int customEventSize = Math.min(customEventSupplier.customEventSize(), maxCustomEventsPerMessage);
        final CustomEventsEncoder customEventsEncoder = bodyEncoder.customEventsCount(customEventSize);

        final Iterator<CustomEvent> customEvents = customEventSupplier.customEventDrainIterator(customEventSize);
        while (customEvents.hasNext())
        {
            final CustomEvent customEvent = customEvents.next();
            final GossipTerm senderGossipTerm = customEvent.getSenderGossipTerm();
            final SocketAddress senderAddress = customEvent.getSenderAddress();
            final DirectBuffer eventType = customEvent.getType();
            final DirectBuffer payload = customEvent.getPayload();

            customEventsEncoder.next()
                .senderGossipEpoch(senderGossipTerm.getEpoch())
                .senderGossipHeartbeat(senderGossipTerm.getHeartbeat())
                .senderPort(senderAddress.port());

            customEventsEncoder
                .putSenderHost(senderAddress.getHostBuffer(), 0, senderAddress.hostLength())
                .putEventType(eventType, 0, eventType.capacity())
                .putPayload(payload, 0, payload.capacity());
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

        final CustomEventsDecoder customEventsDecoder = bodyDecoder.customEvents();
        if (customEventsDecoder.count() > 0)
        {
            for (CustomEventsDecoder customEventDecoder : customEventsDecoder)
            {
                customEvent.getSenderGossipTerm()
                    .epoch(customEventDecoder.senderGossipEpoch())
                    .heartbeat(customEventDecoder.senderGossipHeartbeat());

                final SocketAddress senderAddress = customEvent.getSenderAddress()
                    .port(customEventDecoder.senderPort())
                    .hostLength(customEventDecoder.senderHostLength());

                customEventDecoder.getSenderHost(senderAddress.getHostBuffer(), 0, senderAddress.hostLength());

                final int typeLength = customEventDecoder.eventTypeLength();
                customEvent.typeLength(typeLength);
                customEventDecoder.getEventType(customEvent.getTypeBuffer(), 0, typeLength);

                final int payloadLength = customEventDecoder.payloadLength();
                customEvent.payloadLength(payloadLength);
                customEventDecoder.getPayload(customEvent.getPayloadBuffer(), 0, payloadLength);

                customEventConsumer.consumeCustomEvent(customEvent);
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

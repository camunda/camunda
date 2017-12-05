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

    private final MembershipEventImpl membershipEvent = new MembershipEventImpl();

    private GossipEventType eventType = GossipEventType.NULL_VAL;

    private String sender = null;
    private String probeMember = null;

    private GossipTerm senderGossipTerm = new GossipTerm();

    public GossipEvent(MembershipEventSupplier membershipEventSupplier, MembershipEventConsumer membershipEventConsumer)
    {
        this.membershipEventSupplier = membershipEventSupplier;
        this.membershipEventConsumer = membershipEventConsumer;
    }

    public GossipEventType getEventType()
    {
        return eventType;
    }

    public String getSender()
    {
        return sender;
    }

    public GossipTerm getSenderGossipTerm()
    {
        return senderGossipTerm;
    }

    public String getProbeMember()
    {
        return probeMember;
    }

    public GossipEvent eventType(GossipEventType eventType)
    {
        this.eventType = eventType;
        return this;
    }

    public GossipEvent sender(String sender)
    {
        this.sender = sender;
        return this;
    }

    public GossipEvent senderGossipTerm(GossipTerm term)
    {
        this.senderGossipTerm.epoch(term.getEpoch()).heartbeat(term.getHeartbeat());
        return this;
    }

    public GossipEvent probeMember(String probeMember)
    {
        this.probeMember = probeMember;
        return this;
    }

    public GossipEvent reset()
    {
        this.eventType = GossipEventType.NULL_VAL;
        this.sender = null;
        this.probeMember = null;
        this.senderGossipTerm.epoch(-1L).heartbeat(-1L);

        return this;
    }

    @Override
    public int getLength()
    {
        int length = headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength() +
                GossipEventEncoder.senderHeaderLength() +
                GossipEventEncoder.probeMemberHeaderLength() +
                MembershipEventsEncoder.sbeHeaderSize() +
                CustomEventsEncoder.sbeHeaderSize();

        if (sender != null)
        {
            length += sender.length();
        }

        if (probeMember != null)
        {
            length += probeMember.length();
        }

        // TODO configure the event size
        final Iterator<MembershipEvent> membershipEvents = membershipEventSupplier.membershipEventsView(32);
        while (membershipEvents.hasNext())
        {
            final MembershipEvent event = membershipEvents.next();

            length += event.getMemberId().length();
            length += MembershipEventsEncoder.sbeBlockLength() + MembershipEventsEncoder.addressHeaderLength();
        }

        // TODO add length of custom events

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
            .senderGossipEpoch(senderGossipTerm.getEpoch())
            .senderGossipHeartbeat(senderGossipTerm.getHeartbeat());

        // TODO configure the event size
        final int membershipEventSize = Math.min(membershipEventSupplier.membershipEventSize(), 32);
        final MembershipEventsEncoder membershipEventsEncoder = bodyEncoder.membershipEventsCount(membershipEventSize);

        final Iterator<MembershipEvent> membershipEvents = membershipEventSupplier.drainMembershipEvents(membershipEventSize);
        while (membershipEvents.hasNext())
        {
            final MembershipEvent membershipEvent = membershipEvents.next();

            membershipEventsEncoder.next()
                .eventType(membershipEvent.getType())
                .gossipEpoch(membershipEvent.getGossipTerm().getEpoch())
                .gossipHeartbeat(membershipEvent.getGossipTerm().getHeartbeat())
                .address(membershipEvent.getMemberId());
        }

        if (sender != null)
        {
            bodyEncoder.sender(sender);
        }

        if (probeMember != null)
        {
            bodyEncoder.probeMember(probeMember);
        }
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

        senderGossipTerm.epoch(bodyDecoder.senderGossipEpoch());
        senderGossipTerm.heartbeat(bodyDecoder.senderGossipHeartbeat());

        final MembershipEventsDecoder membershipEventsDecoder = bodyDecoder.membershipEvents();
        if (membershipEventsDecoder.count() > 0)
        {
            for (MembershipEventsDecoder membershipEventDecoder : membershipEventsDecoder)
            {
                final MembershipEventType membershipType = membershipEventDecoder.eventType();
                final long gossipTermEpoch = membershipEventDecoder.gossipEpoch();
                final long gossipTermHeartbeat = membershipEventDecoder.gossipHeartbeat();
                final String address = membershipEventDecoder.address();

                membershipEvent.wrap(address, membershipType, gossipTermEpoch, gossipTermHeartbeat);
                membershipEventConsumer.consumeMembershipEvent(membershipEvent);
            }
        }

        sender = bodyDecoder.sender();
        probeMember = bodyDecoder.probeMember();
    }

}

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

import static io.zeebe.clustering.gossip.GossipEventEncoder.probeMemberIdNullValue;
import static io.zeebe.clustering.gossip.GossipEventEncoder.senderIdNullValue;

import io.zeebe.clustering.gossip.GossipEventDecoder;
import io.zeebe.clustering.gossip.GossipEventDecoder.CustomEventsDecoder;
import io.zeebe.clustering.gossip.GossipEventDecoder.MembershipEventsDecoder;
import io.zeebe.clustering.gossip.GossipEventEncoder;
import io.zeebe.clustering.gossip.GossipEventEncoder.CustomEventsEncoder;
import io.zeebe.clustering.gossip.GossipEventEncoder.MembershipEventsEncoder;
import io.zeebe.clustering.gossip.GossipEventType;
import io.zeebe.clustering.gossip.MessageHeaderDecoder;
import io.zeebe.clustering.gossip.MessageHeaderEncoder;
import io.zeebe.gossip.membership.GossipTerm;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import java.util.Iterator;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class GossipEvent implements BufferReader, BufferWriter {
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

  private int senderId;
  private int probeMemberId;

  private GossipEventType eventType = GossipEventType.NULL_VAL;

  public GossipEvent(
      MembershipEventSupplier membershipEventSupplier,
      MembershipEventConsumer membershipEventConsumer,
      CustomEventSupplier customEventSupplier,
      CustomEventConsumer customEventConsumer,
      int maxMembershipEventsPerMessage,
      int maxCustomEventsPerMessage) {
    this.membershipEventSupplier = membershipEventSupplier;
    this.membershipEventConsumer = membershipEventConsumer;
    this.maxMembershipEventsPerMessage = maxMembershipEventsPerMessage;

    this.customEventSupplier = customEventSupplier;
    this.customEventConsumer = customEventConsumer;
    this.maxCustomEventsPerMessage = maxCustomEventsPerMessage;
  }

  public GossipEventType getEventType() {
    return eventType;
  }

  public int getSenderId() {
    return senderId;
  }

  public int getProbeMemberId() {
    return probeMemberId;
  }

  public GossipEvent eventType(GossipEventType eventType) {
    this.eventType = eventType;
    return this;
  }

  public GossipEvent senderId(int senderId) {
    this.senderId = senderId;
    return this;
  }

  public GossipEvent probeMemberId(int probeMemberId) {
    this.probeMemberId = probeMemberId;
    return this;
  }

  public GossipEvent reset() {
    this.eventType = GossipEventType.NULL_VAL;
    this.senderId = senderIdNullValue();
    this.probeMemberId = probeMemberIdNullValue();

    return this;
  }

  @Override
  public int getLength() {
    int length =
        headerEncoder.encodedLength()
            + bodyEncoder.sbeBlockLength()
            + MembershipEventsEncoder.sbeHeaderSize()
            + CustomEventsEncoder.sbeHeaderSize();

    final Iterator<MembershipEvent> membershipEvents =
        membershipEventSupplier.membershipEventViewIterator(maxMembershipEventsPerMessage);
    while (membershipEvents.hasNext()) {
      membershipEvents.next();

      length += MembershipEventsEncoder.sbeBlockLength();
    }

    final Iterator<CustomEvent> customEvents =
        customEventSupplier.customEventViewIterator(maxCustomEventsPerMessage);
    while (customEvents.hasNext()) {
      final CustomEvent event = customEvents.next();

      length +=
          event.getType().capacity()
              + event.getPayload().capacity()
              + CustomEventsEncoder.sbeBlockLength()
              + CustomEventsEncoder.eventTypeHeaderLength()
              + CustomEventsEncoder.payloadHeaderLength();
    }

    return length;
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    headerEncoder
        .wrap(buffer, offset)
        .blockLength(bodyEncoder.sbeBlockLength())
        .templateId(bodyEncoder.sbeTemplateId())
        .schemaId(bodyEncoder.sbeSchemaId())
        .version(bodyEncoder.sbeSchemaVersion());

    bodyEncoder.wrap(buffer, offset + headerEncoder.encodedLength());

    bodyEncoder.eventType(eventType).senderId(senderId).probeMemberId(probeMemberId);

    // TODO sort membership events by spread count if event count is greater that the given limit
    final int membershipEventSize =
        Math.min(membershipEventSupplier.membershipEventSize(), maxMembershipEventsPerMessage);
    final MembershipEventsEncoder membershipEventsEncoder =
        bodyEncoder.membershipEventsCount(membershipEventSize);

    final Iterator<MembershipEvent> membershipEvents =
        membershipEventSupplier.membershipEventDrainIterator(membershipEventSize);
    while (membershipEvents.hasNext()) {
      final MembershipEvent membershipEvent = membershipEvents.next();
      final GossipTerm gossipTerm = membershipEvent.getGossipTerm();

      membershipEventsEncoder
          .next()
          .eventType(membershipEvent.getType())
          .gossipEpoch(gossipTerm.getEpoch())
          .gossipHeartbeat(gossipTerm.getHeartbeat())
          .memberId(membershipEvent.getMemberId());
    }

    // TODO sort custom events by spread count if event count is greater that the given limit
    final int customEventSize =
        Math.min(customEventSupplier.customEventSize(), maxCustomEventsPerMessage);
    final CustomEventsEncoder customEventsEncoder = bodyEncoder.customEventsCount(customEventSize);

    final Iterator<CustomEvent> customEvents =
        customEventSupplier.customEventDrainIterator(customEventSize);
    while (customEvents.hasNext()) {
      final CustomEvent customEvent = customEvents.next();
      final GossipTerm senderGossipTerm = customEvent.getSenderGossipTerm();
      final int senderId = customEvent.getSenderId();
      final DirectBuffer eventType = customEvent.getType();
      final DirectBuffer payload = customEvent.getPayload();

      customEventsEncoder
          .next()
          .senderGossipEpoch(senderGossipTerm.getEpoch())
          .senderGossipHeartbeat(senderGossipTerm.getHeartbeat())
          .senderId(senderId);

      customEventsEncoder
          .putEventType(eventType, 0, eventType.capacity())
          .putPayload(payload, 0, payload.capacity());
    }
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    reset();

    headerDecoder.wrap(buffer, offset);

    offset += headerDecoder.encodedLength();

    bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    eventType = bodyDecoder.eventType();
    senderId = bodyDecoder.senderId();
    probeMemberId = bodyDecoder.probeMemberId();

    final MembershipEventsDecoder membershipEventsDecoder = bodyDecoder.membershipEvents();
    if (membershipEventsDecoder.count() > 0) {
      for (MembershipEventsDecoder membershipEventDecoder : membershipEventsDecoder) {
        membershipEvent.type(membershipEventDecoder.eventType());

        membershipEvent
            .getGossipTerm()
            .epoch(membershipEventDecoder.gossipEpoch())
            .heartbeat(membershipEventDecoder.gossipHeartbeat());

        membershipEvent.memberId(membershipEventDecoder.memberId());

        membershipEventConsumer.consumeMembershipEvent(membershipEvent);
      }
    }

    final CustomEventsDecoder customEventsDecoder = bodyDecoder.customEvents();
    if (customEventsDecoder.count() > 0) {
      for (CustomEventsDecoder customEventDecoder : customEventsDecoder) {
        customEvent
            .getSenderGossipTerm()
            .epoch(customEventDecoder.senderGossipEpoch())
            .heartbeat(customEventDecoder.senderGossipHeartbeat());

        customEvent.senderId(customEventDecoder.senderId());

        final int typeLength = customEventDecoder.eventTypeLength();
        customEvent.typeLength(typeLength);
        customEventDecoder.getEventType(customEvent.getTypeBuffer(), 0, typeLength);

        final int payloadLength = customEventDecoder.payloadLength();
        customEvent.payloadLength(payloadLength);
        customEventDecoder.getPayload(customEvent.getPayloadBuffer(), 0, payloadLength);

        customEventConsumer.consumeCustomEvent(customEvent);
      }
    }
  }
}

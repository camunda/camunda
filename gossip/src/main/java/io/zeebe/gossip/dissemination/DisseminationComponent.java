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
package io.zeebe.gossip.dissemination;

import io.zeebe.gossip.GossipConfiguration;
import io.zeebe.gossip.GossipMath;
import io.zeebe.gossip.GossipMembershipListener;
import io.zeebe.gossip.membership.Member;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.gossip.protocol.CustomEvent;
import io.zeebe.gossip.protocol.CustomEventConsumer;
import io.zeebe.gossip.protocol.CustomEventSupplier;
import io.zeebe.gossip.protocol.MembershipEvent;
import io.zeebe.gossip.protocol.MembershipEventConsumer;
import io.zeebe.gossip.protocol.MembershipEventSupplier;
import io.zeebe.util.collection.ReusableObjectList;
import java.util.Iterator;

public class DisseminationComponent
    implements MembershipEventSupplier,
        MembershipEventConsumer,
        CustomEventSupplier,
        CustomEventConsumer {
  private final GossipConfiguration configuration;
  private final MembershipList memberList;

  private final ReusableObjectList<BufferedEvent<MembershipEvent>> membershipEventBuffer;
  private final ReusableObjectList<BufferedEvent<CustomEvent>> customEventBuffer;

  private final BufferedEventIterator<MembershipEvent> membershipEventViewIterator =
      new BufferedEventIterator<>(false);
  private final BufferedEventIterator<MembershipEvent> membershipEventDrainIterator =
      new BufferedEventIterator<>(true);

  private final BufferedEventIterator<CustomEvent> customEventViewIterator =
      new BufferedEventIterator<>(false);
  private final BufferedEventIterator<CustomEvent> customEventDrainIterator =
      new BufferedEventIterator<>(true);

  public DisseminationComponent(GossipConfiguration configuration, MembershipList memberList) {
    this.configuration = configuration;
    this.memberList = memberList;

    this.membershipEventBuffer =
        new ReusableObjectList<>(() -> new BufferedEvent<>(new MembershipEvent()));
    this.customEventBuffer = new ReusableObjectList<>(() -> new BufferedEvent<>(new CustomEvent()));

    memberList.addListener(
        new GossipMembershipListener() {

          @Override
          public void onRemove(Member member) {
            updateEventSpreadLimit();
          }

          @Override
          public void onAdd(Member member) {
            updateEventSpreadLimit();
          }
        });
  }

  private void updateEventSpreadLimit() {
    final int clusterSize = 1 + memberList.size();
    final int multiplier = configuration.getRetransmissionMultiplier();

    final int spreadLimit = GossipMath.gossipPeriodsToSpread(multiplier, clusterSize);

    membershipEventDrainIterator.setSpreadLimit(spreadLimit);
    customEventDrainIterator.setSpreadLimit(spreadLimit);
  }

  public MembershipEvent addMembershipEvent() {
    return membershipEventBuffer.add().getEvent();
  }

  public CustomEvent addCustomEvent() {
    return customEventBuffer.add().getEvent();
  }

  @Override
  public int membershipEventSize() {
    return membershipEventBuffer.size();
  }

  @Override
  public Iterator<MembershipEvent> membershipEventViewIterator(int max) {
    membershipEventViewIterator.wrap(membershipEventBuffer.iterator(), max);

    return membershipEventViewIterator;
  }

  @Override
  public Iterator<MembershipEvent> membershipEventDrainIterator(int max) {
    membershipEventDrainIterator.wrap(membershipEventBuffer.iterator(), max);

    return membershipEventDrainIterator;
  }

  @Override
  public int customEventSize() {
    return customEventBuffer.size();
  }

  @Override
  public Iterator<CustomEvent> customEventViewIterator(int max) {
    customEventViewIterator.wrap(customEventBuffer.iterator(), max);

    return customEventViewIterator;
  }

  @Override
  public Iterator<CustomEvent> customEventDrainIterator(int max) {
    customEventDrainIterator.wrap(customEventBuffer.iterator(), max);

    return customEventDrainIterator;
  }

  @Override
  public boolean consumeMembershipEvent(MembershipEvent event) {
    addMembershipEvent()
        .type(event.getType())
        .memberId(event.getMemberId())
        .gossipTerm(event.getGossipTerm());

    return true;
  }

  @Override
  public boolean consumeCustomEvent(CustomEvent event) {
    addCustomEvent()
        .senderId(event.getSenderId())
        .senderGossipTerm(event.getSenderGossipTerm())
        .type(event.getType())
        .payload(event.getPayload());

    return true;
  }
}

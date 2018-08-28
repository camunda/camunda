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

import io.zeebe.gossip.membership.GossipTerm;
import io.zeebe.gossip.membership.Member;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.gossip.protocol.CustomEvent;
import io.zeebe.gossip.protocol.CustomEventConsumer;
import org.agrona.DirectBuffer;

public class MembershipCustomEventUpdater implements CustomEventConsumer {
  private final MembershipList membershipList;

  public MembershipCustomEventUpdater(MembershipList membershipList) {
    this.membershipList = membershipList;
  }

  @Override
  public boolean consumeCustomEvent(CustomEvent event) {
    boolean isNew = false;

    final int senderId = event.getSenderId();
    final DirectBuffer eventType = event.getType();
    final GossipTerm senderGossipTerm = event.getSenderGossipTerm();

    if (membershipList.hasMember(senderId)) {
      final Member member = membershipList.get(senderId);
      final GossipTerm currentTerm = member.getTermForEventType(eventType);

      if (currentTerm == null) {
        member.addTermForEventType(eventType, senderGossipTerm);

        isNew = true;
      } else if (senderGossipTerm.isGreaterThan(currentTerm)) {
        currentTerm.wrap(senderGossipTerm);

        isNew = true;
      }
    }

    return isNew;
  }
}

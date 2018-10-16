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

import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.membership.GossipTerm;
import io.zeebe.gossip.membership.Member;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.gossip.membership.MembershipStatus;
import io.zeebe.gossip.protocol.MembershipEvent;
import io.zeebe.gossip.protocol.MembershipEventSupplier;
import java.util.Iterator;

public class MembershipListEventSupplier implements MembershipEventSupplier {
  private final MembershipList membershipList;
  private final MembershipEventIterator iterator;

  public MembershipListEventSupplier(MembershipList membershipList) {
    this.membershipList = membershipList;
    this.iterator = new MembershipEventIterator(membershipList);
  }

  @Override
  public int membershipEventSize() {
    return 1 + membershipList.size();
  }

  @Override
  public Iterator<MembershipEvent> membershipEventViewIterator(int max) {
    iterator.reset();

    return iterator;
  }

  @Override
  public Iterator<MembershipEvent> membershipEventDrainIterator(int max) {
    return membershipEventViewIterator(max);
  }

  private class MembershipEventIterator implements Iterator<MembershipEvent> {
    private final MembershipEvent membershipEvent = new MembershipEvent();

    private final Member self;

    private Iterator<Member> iterator;
    private int index = 0;

    MembershipEventIterator(MembershipList membershipList) {
      this.self = membershipList.self();
    }

    public void reset() {
      iterator = membershipList.iterator();
      index = 0;
    }

    @Override
    public boolean hasNext() {
      return index == 0 || iterator.hasNext();
    }

    @Override
    public MembershipEvent next() {
      Member member = null;
      if (index == 0) {
        member = self;
      } else {
        member = iterator.next();
      }

      final MembershipEventType eventType = resolveType(member.getStatus());
      if (eventType != null) {
        membershipEvent.type(eventType);

        final GossipTerm gossipTerm = member.getTerm();
        membershipEvent.getGossipTerm().wrap(gossipTerm);

        membershipEvent.memberId(member.getId());
      }

      index += 1;

      return membershipEvent;
    }

    private MembershipEventType resolveType(MembershipStatus status) {
      switch (status) {
        case SUSPECT:
          return MembershipEventType.SUSPECT;
        case ALIVE:
          return MembershipEventType.ALIVE;
        default:
          return null;
      }
    }
  }
}

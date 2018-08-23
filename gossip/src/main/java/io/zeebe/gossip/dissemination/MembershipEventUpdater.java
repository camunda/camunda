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
import io.zeebe.gossip.Loggers;
import io.zeebe.gossip.membership.Member;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.gossip.membership.MembershipStatus;
import io.zeebe.gossip.protocol.MembershipEvent;
import io.zeebe.gossip.protocol.MembershipEventConsumer;
import org.slf4j.Logger;

public final class MembershipEventUpdater implements MembershipEventConsumer {
  private static final Logger LOG = Loggers.GOSSIP_LOGGER;

  private final MembershipList membershipList;
  private final DisseminationComponent disseminationComponent;

  public MembershipEventUpdater(
      MembershipList memberList, DisseminationComponent disseminationComponent) {
    this.membershipList = memberList;
    this.disseminationComponent = disseminationComponent;
  }

  @Override
  public boolean consumeMembershipEvent(MembershipEvent event) {
    boolean changed = false;

    final Member self = membershipList.self();

    if (event.getMemberId() == self.getId()) {
      if (event.getType() == MembershipEventType.SUSPECT
          && event.getGossipTerm().isEqual(self.getTerm())) {
        // need to increment term when update the status
        self.getTerm().increment();

        LOG.debug("Spread ALIVE event");

        disseminationComponent
            .addMembershipEvent()
            .memberId(self.getId())
            .type(MembershipEventType.ALIVE)
            .gossipTerm(self.getTerm());
      }
    } else if (membershipList.hasMember(event.getMemberId())) {
      changed = updateMembership(event);
    } else {
      changed = addNewMember(event);
    }

    return changed;
  }

  private boolean updateMembership(MembershipEvent event) {
    boolean changed = false;

    final Member member = membershipList.get(event.getMemberId());

    switch (event.getType()) {
      case JOIN:
      case ALIVE:
        {
          if (event.getGossipTerm().isGreaterThan(member.getTerm())) {
            LOG.debug(
                "Update member '{}', status = ALIVE, gossip-term: {}",
                member.getId(),
                event.getGossipTerm());

            membershipList.aliveMember(member.getId(), event.getGossipTerm());
            changed = true;
          }
          break;
        }
      case SUSPECT:
        {
          switch (member.getStatus()) {
            case SUSPECT:
              {
                if (event.getGossipTerm().isGreaterThan(member.getTerm())) {
                  LOG.debug(
                      "Update member '{}', status = SUSPECT, gossip-term: {}",
                      member.getId(),
                      event.getGossipTerm());

                  membershipList.suspectMember(member.getId(), event.getGossipTerm());
                  changed = true;
                }
                break;
              }
            case ALIVE:
              {
                if (event.getGossipTerm().isEqual(member.getTerm())
                    || event.getGossipTerm().isGreaterThan(member.getTerm())) {
                  LOG.debug(
                      "Update member '{}', status = SUSPECT, gossip-term: {}",
                      member.getId(),
                      event.getGossipTerm());

                  membershipList.aliveMember(member.getId(), event.getGossipTerm());
                  changed = true;
                }
                break;
              }
            default:
              break;
          }
          break;
        }
      case CONFIRM:
      case LEAVE:
        {
          if (member.getStatus() != MembershipStatus.DEAD
              && event.getGossipTerm().isGreaterThan(member.getTerm())) {
            LOG.info(
                "Remove member '{}', status = {}, gossip-term: {}",
                member.getId(),
                event.getType(),
                event.getGossipTerm());

            membershipList.removeMember(member.getId());
            changed = true;
          }
          break;
        }
      default:
        break;
    }

    return changed;
  }

  private boolean addNewMember(MembershipEvent event) {
    boolean changed = false;

    switch (event.getType()) {
      case JOIN:
      case ALIVE:
        {
          LOG.info(
              "Add member '{}' with status ALIVE, gossip-term: {}",
              event.getMemberId(),
              event.getGossipTerm());

          membershipList.newMember(event.getMemberId(), event.getGossipTerm());
          changed = true;
          break;
        }
      case SUSPECT:
        {
          LOG.info(
              "Add member '{}' with status SUSPECT, gossip-term: {}",
              event.getMemberId(),
              event.getGossipTerm());

          final Member member =
              membershipList.newMember(event.getMemberId(), event.getGossipTerm());
          membershipList.suspectMember(member.getId(), event.getGossipTerm());
          changed = true;
          break;
        }
      default:
        break;
    }

    return changed;
  }
}

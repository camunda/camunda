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
package io.zeebe.gossip.membership;

import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.Loggers;
import io.zeebe.gossip.dissemination.DisseminationComponent;
import io.zeebe.gossip.protocol.MembershipEvent;
import io.zeebe.gossip.protocol.MembershipEventConsumer;
import org.slf4j.Logger;

public final class MembershipUpdater implements MembershipEventConsumer
{
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;

    private final MembershipList memberList;
    private final DisseminationComponent disseminationComponent;

    public MembershipUpdater(MembershipList memberList, DisseminationComponent disseminationComponent)
    {
        this.memberList = memberList;
        this.disseminationComponent = disseminationComponent;
    }

    @Override
    public boolean consumeMembershipEvent(MembershipEvent event)
    {
        boolean changed = false;

        if (event.getAddress().equals(memberList.self().getAddress()))
        {
            final Member self = memberList.self();

            if (event.getType() == MembershipEventType.SUSPECT && event.getGossipTerm().isEqual(self.getTerm()))
            {
                // need to increment term when update the status
                self.getTerm().increment();

                LOG.debug("Spread ALIVE event with gossip-term: {}", self.getTerm());

                disseminationComponent.addMembershipEvent()
                    .address(self.getAddress())
                    .type(MembershipEventType.ALIVE)
                    .gossipTerm(self.getTerm());
            }
        }
        else if (memberList.hasMember(event.getAddress()))
        {
            changed = updateMembership(event);
        }
        else
        {
            changed = addNewMember(event);
        }

        return changed;
    }

    private boolean updateMembership(MembershipEvent event)
    {
        boolean changed = false;

        final Member member = memberList.get(event.getAddress());

        switch (event.getType())
        {
            case JOIN:
            case ALIVE:
            {
                if (event.getGossipTerm().isGreaterThan(member.getTerm()))
                {
                    LOG.debug("Update member '{}', status = ALIVE, gossip-term: {}", member.getId(), event.getGossipTerm());

                    memberList.aliveMember(member.getId(), event.getGossipTerm());
                    changed = true;
                }
                break;
            }
            case SUSPECT:
            {
                switch (member.getStatus())
                {
                    case SUSPECT:
                    {
                        if (event.getGossipTerm().isGreaterThan(member.getTerm()))
                        {
                            LOG.debug("Update member '{}', status = SUSPECT, gossip-term: {}", member.getId(), event.getGossipTerm());

                            memberList.suspectMember(member.getId(), event.getGossipTerm());
                            changed = true;
                        }
                        break;
                    }
                    case ALIVE:
                    {
                        if (event.getGossipTerm().isEqual(member.getTerm()) || event.getGossipTerm().isGreaterThan(member.getTerm()))
                        {
                            LOG.debug("Update member '{}', status = SUSPECT, gossip-term: {}", member.getId(), event.getGossipTerm());

                            memberList.aliveMember(member.getId(), event.getGossipTerm());
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
                if (member.getStatus() != MembershipStatus.DEAD)
                {
                    LOG.info("Remove member '{}', status = {}, gossip-term: {}", member.getId(), event.getType(), event.getGossipTerm());

                    memberList.removeMember(member.getId());
                    changed = true;
                }
                break;
            }
            default:
                break;
        }

        return changed;
    }


    private boolean addNewMember(MembershipEvent event)
    {
        boolean changed = false;

        switch (event.getType())
        {
            case JOIN:
            case ALIVE:
            {
                LOG.info("Add member '{}' with status ALIVE, gossip-term: {}", event.getAddress(), event.getGossipTerm());

                memberList.newMember(event.getAddress(), event.getGossipTerm());
                changed = true;
                break;
            }
            case SUSPECT:
            {
                LOG.info("Add member '{}' with status SUSPECT, gossip-term: {}", event.getAddress(), event.getGossipTerm());

                final Member member = memberList.newMember(event.getAddress(), event.getGossipTerm());
                memberList.suspectMember(member.getId(), event.getGossipTerm());
                changed = true;
                break;
            }
            default:
                break;
        }

        return changed;
    }

}
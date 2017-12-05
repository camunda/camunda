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
import io.zeebe.gossip.dissemination.DisseminationComponent;
import io.zeebe.gossip.protocol.MembershipEvent;
import io.zeebe.gossip.protocol.MembershipEventConsumer;

public final class MembershipUpdater implements MembershipEventConsumer
{
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

        if (event.getMemberId().equals(memberList.self().getId()))
        {
            if (event.getType() == MembershipEventType.SUSPECT)
            {
                final Member self = memberList.self();
                // need to increment term when update the status
                self.getTerm().increment();

                disseminationComponent.addMembershipEvent()
                    .memberId(self.getId())
                    .type(MembershipEventType.ALIVE)
                    .gossipTerm(self.getTerm());
            }
        }
        else if (memberList.hasMember(event.getMemberId()))
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

        final Member member = memberList.get(event.getMemberId());

        switch (event.getType())
        {
            case JOIN:
            case ALIVE:
            {
                if (event.getGossipTerm().isGreaterThan(member.getTerm()))
                {
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
                            memberList.suspectMember(member.getId(), event.getGossipTerm());
                            changed = true;
                        }
                        break;
                    }
                    case ALIVE:
                    {
                        if (event.getGossipTerm().isEqual(member.getTerm()) || event.getGossipTerm().isGreaterThan(member.getTerm()))
                        {
                            memberList.aliveMember(member.getId(), event.getGossipTerm());
                            changed = true;
                        }
                        break;
                    }
                }
                break;
            }
            case CONFIRM:
            case LEAVE:
            {
                memberList.removeMember(member.getId());
                changed = true;
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
                memberList.newMember(event.getMemberId(), event.getGossipTerm());
                changed = true;
                break;
            }
            case SUSPECT:
            {
                final Member member = memberList.newMember(event.getMemberId(), event.getGossipTerm());
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
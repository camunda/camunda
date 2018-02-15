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

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.GossipConfiguration;
import io.zeebe.gossip.membership.GossipTerm;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.gossip.membership.MembershipStatus;
import io.zeebe.gossip.protocol.MembershipEvent;
import io.zeebe.transport.SocketAddress;
import org.junit.Test;

/**
 *
 */
public class MembershipEventUpdaterTest
{
    private static final GossipConfiguration CONFIGURATION = new GossipConfiguration();

    private MembershipList members = new MembershipList(new SocketAddress(), (member) -> { });
    private DisseminationComponent disseminationComponent = new DisseminationComponent(CONFIGURATION, members);
    private MembershipEventUpdater membershipEventUpdater = new MembershipEventUpdater(members, disseminationComponent);

    @Test
    public void shouldNotConsumeOldRemoveEvent()
    {
        // given
        final SocketAddress memberAddress = new SocketAddress("localhost", 8181);
        final GossipTerm oldTerm = new GossipTerm();
        oldTerm.epoch(System.currentTimeMillis() - 1000);
        final GossipTerm newTerm = new GossipTerm();
        members.newMember(memberAddress, newTerm);

        // when
        final MembershipEvent membershipEvent = new MembershipEvent();
        membershipEvent.address(memberAddress);
        membershipEvent.type(MembershipEventType.LEAVE);
        membershipEvent.gossipTerm(oldTerm);

        final boolean consumed = membershipEventUpdater.consumeMembershipEvent(membershipEvent);

        // then
        assertThat(consumed).isFalse();
        assertThat(members.get(memberAddress).getStatus()).isEqualByComparingTo(MembershipStatus.ALIVE);
    }

}

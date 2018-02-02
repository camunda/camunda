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

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gossip.GossipConfiguration;
import io.zeebe.transport.SocketAddress;
import org.junit.Test;

public class MembershipListTest
{
    private static final GossipConfiguration CONFIGURATION = new GossipConfiguration();

    private MembershipList members = new MembershipList(new SocketAddress(), CONFIGURATION);

    @Test
    public void shouldCallMembershipListener()
    {
        // given
        final SocketAddress localhost = new SocketAddress("localhost", 51015);
        final SimpleMembershipListener listener = new SimpleMembershipListener();
        members.addListener(listener);


        // when
        members.newMember(localhost, new GossipTerm());

        // then
        assertThat(listener.memberList.size()).isEqualTo(1);
        assertThat(listener.memberList).containsExactly(members.get(localhost));
    }

    @Test
    public void shouldCallMembershipListenerEventMemberWasAddedBefore()
    {
        // given
        final SocketAddress localhost = new SocketAddress("localhost", 51015);
        members.newMember(localhost, new GossipTerm());

        // when
        final SimpleMembershipListener listener = new SimpleMembershipListener();
        members.addListener(listener);

        // then
        assertThat(listener.memberList.size()).isEqualTo(1);
        assertThat(listener.memberList).containsExactly(members.get(localhost));
    }

}

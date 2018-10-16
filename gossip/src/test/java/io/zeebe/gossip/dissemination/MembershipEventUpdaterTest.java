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
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** */
public class MembershipEventUpdaterTest {
  private static final GossipConfiguration CONFIGURATION = new GossipConfiguration();

  private MembershipList membershipList;
  private DisseminationComponent disseminationComponent;
  private MembershipEventUpdater membershipEventUpdater;
  private final int memberId = 123;

  @Rule public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();

  @Before
  public void setUp() {
    // Needs to be done inside an actor, because we need access to the ActorClock in GossipTerm
    actorSchedulerRule
        .submitActor(
            new Actor() {
              @Override
              protected void onActorStarting() {
                membershipList = new MembershipList(0, (member) -> {});

                disseminationComponent = new DisseminationComponent(CONFIGURATION, membershipList);
                membershipEventUpdater =
                    new MembershipEventUpdater(membershipList, disseminationComponent);
              }
            })
        .join();
  }

  private boolean consumed = false;

  @Test
  public void shouldNotConsumeOldRemoveEvent() {
    // given
    actorSchedulerRule
        .submitActor(
            new Actor() {
              @Override
              protected void onActorStarting() {
                final GossipTerm oldTerm = new GossipTerm().epoch(1);
                final GossipTerm newTerm = new GossipTerm().epoch(2);

                membershipList.newMember(memberId, newTerm);

                // when
                final MembershipEvent membershipEvent = new MembershipEvent();
                membershipEvent.memberId(memberId);
                membershipEvent.type(MembershipEventType.LEAVE);
                membershipEvent.gossipTerm(oldTerm);

                consumed = membershipEventUpdater.consumeMembershipEvent(membershipEvent);
              }
            })
        .join();

    // then
    assertThat(consumed).isFalse();
    assertThat(membershipList.get(memberId).getStatus())
        .isEqualByComparingTo(MembershipStatus.ALIVE);
  }
}

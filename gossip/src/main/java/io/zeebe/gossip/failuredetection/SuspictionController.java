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
package io.zeebe.gossip.failuredetection;

import java.util.Iterator;

import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.GossipContext;
import io.zeebe.gossip.Loggers;
import io.zeebe.gossip.dissemination.DisseminationComponent;
import io.zeebe.gossip.membership.*;
import io.zeebe.util.state.*;
import io.zeebe.util.time.ClockUtil;
import org.slf4j.Logger;

public class SuspictionController
{
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;

    private static final int TRANSITION_DEFAULT = 0;

    private final MembershipList memberList;

    private final StateMachine<SimpleStateMachineContext> stateMachine;

    public SuspictionController(GossipContext context)
    {
        this.memberList = context.getMemberList();

        // check suspicion in the same interval as probing
        final int checkInterval = context.getConfiguration().getProbeInterval();

        final AwaitNextIntervalState awaitNextIntervalState = new AwaitNextIntervalState(checkInterval);
        final CheckSuspictionTimeoutState checkSuspictionTimeoutState = new CheckSuspictionTimeoutState(context.getDisseminationComponent());

        stateMachine = StateMachine.builder()
                .initialState(awaitNextIntervalState)
                .from(awaitNextIntervalState).take(TRANSITION_DEFAULT).to(checkSuspictionTimeoutState)
                .from(checkSuspictionTimeoutState).take(TRANSITION_DEFAULT).to(awaitNextIntervalState)
                .build();
    }

    public int doWork()
    {
        return stateMachine.doWork();
    }

    private class AwaitNextIntervalState implements WaitState<SimpleStateMachineContext>
    {
        private final long interval;
        private long nextInterval;

        AwaitNextIntervalState(long interval)
        {
            this.interval = interval;
        }

        @Override
        public void work(SimpleStateMachineContext context) throws Exception
        {
            final long currentTime = ClockUtil.getCurrentTimeInMillis();

            if (currentTime >= nextInterval)
            {
                nextInterval = currentTime + interval;
                context.take(TRANSITION_DEFAULT);
            }
        }
    }

    private class CheckSuspictionTimeoutState implements State<SimpleStateMachineContext>
    {
        private final DisseminationComponent disseminationComponent;

        CheckSuspictionTimeoutState(DisseminationComponent disseminationComponent)
        {
            this.disseminationComponent = disseminationComponent;
        }

        @Override
        public int doWork(SimpleStateMachineContext context) throws Exception
        {
            int workCount = 0;

            final long currentTime = ClockUtil.getCurrentTimeInMillis();

            final Iterator<Member> members = memberList.iterator();
            while (members.hasNext())
            {
                final Member member = members.next();

                if (member.getStatus() == MembershipStatus.SUSPECT && currentTime >= member.getSuspectTimeout())
                {
                    LOG.info("Remove suspicious member '{}'", member.getId());

                    members.remove();

                    LOG.trace("Spread CONFIRM event about '{}'", member.getId());

                    disseminationComponent.addMembershipEvent()
                        .memberId(member.getId())
                        .gossipTerm(member.getTerm())
                        .type(MembershipEventType.CONFIRM);

                    workCount += 1;
                }
            }

            context.take(TRANSITION_DEFAULT);
            return workCount;
        }
    }

}

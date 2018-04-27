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
package io.zeebe.gossip.util;

import static org.assertj.core.api.Assertions.fail;

import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;

import io.zeebe.gossip.GossipConfiguration;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class GossipClusterRule implements TestRule
{
    private static final int MAX_CONDITION_RETRIES = 100;

    private final ActorSchedulerRule actorScheduler;
    private final ControlledActorClock clock;
    private final GossipConfiguration configuration;
    private final List<GossipRule> gossips;

    public GossipClusterRule(final GossipRule... gossips)
    {
        this(new GossipConfiguration().setProbeTimeout(Duration.ofSeconds(2).toMillis()), gossips);
    }

    public GossipClusterRule(final GossipConfiguration configuration, final GossipRule... gossips)
    {
        this.clock = new ControlledActorClock();
        this.actorScheduler = new ActorSchedulerRule(clock);
        this.configuration = configuration;

        this.gossips = gossips != null ? new ArrayList<>(Arrays.asList(gossips)) : Collections.emptyList();

        this.gossips.forEach(g -> g.init(actorScheduler.get(), configuration));
    }

    @Override
    public Statement apply(Statement base, final Description description)
    {
        final List<TestRule> rules = new ArrayList<>();
        rules.add(actorScheduler);
        rules.addAll(gossips);
        Collections.reverse(rules);

        for (final TestRule rule : rules)
        {
            base = rule.apply(base, description);
        }

        return base;
    }

    public void interruptConnectionBetween(GossipRule thisMember, GossipRule thatMember)
    {
        thisMember.interruptConnectionTo(thatMember);
        thatMember.interruptConnectionTo(thisMember);
    }

    public void reconnect(GossipRule thisMember, GossipRule thatMember)
    {
        thisMember.reconnectTo(thatMember);
        thatMember.reconnectTo(thisMember);
    }

    public void waitUntil(BooleanSupplier condition)
    {
        int i = 0;

        while (!condition.getAsBoolean() && i < MAX_CONDITION_RETRIES)
        {
            clock.addTime(configuration.getProbeIntervalDuration());

            try
            {
                Thread.sleep(10L);
            }
            catch (InterruptedException e)
            {
            }

            i += 1;
        }

        if (i == MAX_CONDITION_RETRIES)
        {
            fail("condition is not satisfied");
        }
    }

}

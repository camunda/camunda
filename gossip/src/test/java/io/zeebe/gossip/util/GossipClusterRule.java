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

import java.util.*;

import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class GossipClusterRule implements TestRule
{
    private final ExternalResource actorScheduler;
    private final List<GossipRule> gossips;

    public GossipClusterRule(final ExternalResource actorScheduler, final GossipRule... gossips)
    {
        this.actorScheduler = actorScheduler;
        this.gossips = gossips != null ? new ArrayList<>(Arrays.asList(gossips)) : Collections.emptyList();
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

}

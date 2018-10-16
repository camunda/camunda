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

import io.zeebe.gossip.GossipConfiguration;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class GossipClusterRule implements TestRule {
  private static final int MAX_CONDITION_RETRIES = 1000;

  private final ActorSchedulerRule actorScheduler;
  private final ControlledActorClock clock;
  private final GossipConfiguration configuration;
  private final List<GossipRule> gossips;

  public GossipClusterRule(final GossipRule... gossips) {
    this(new GossipConfiguration().setProbeTimeout("2s"), gossips);
  }

  public GossipClusterRule(final GossipConfiguration configuration, final GossipRule... gossips) {
    this.clock = new ControlledActorClock();
    this.actorScheduler = new ActorSchedulerRule(clock);
    this.configuration = configuration;

    this.gossips =
        gossips != null ? new ArrayList<>(Arrays.asList(gossips)) : Collections.emptyList();

    this.gossips.forEach(g -> g.init(actorScheduler.get(), configuration));
  }

  @Override
  public Statement apply(Statement base, final Description description) {
    final List<TestRule> rules = new ArrayList<>();
    rules.add(actorScheduler);
    rules.addAll(gossips);
    rules.add(
        new ExternalResource() {
          @Override
          protected void before() throws Throwable {
            // register node endpoints between all gossips
            final int size = gossips.size();
            for (int from = 0; from < size - 1; from++) {
              for (int to = from + 1; to < size; to++) {
                gossips.get(from).reconnectTo(gossips.get(to));
              }
            }
          }
        });

    Collections.reverse(rules);

    for (final TestRule rule : rules) {
      base = rule.apply(base, description);
    }

    return base;
  }

  public void interruptConnectionBetween(GossipRule thisMember, GossipRule thatMember) {
    thisMember.interruptConnectionTo(thatMember);
  }

  public void reconnect(GossipRule thisMember, GossipRule thatMember) {
    thisMember.reconnectTo(thatMember);
  }

  public void waitUntil(BooleanSupplier condition) {
    waitUntil(condition, MAX_CONDITION_RETRIES);
  }

  public void waitUntil(BooleanSupplier condition, int retries) {
    int i = 0;

    while (!condition.getAsBoolean() && i < retries) {
      clock.addTime(configuration.getProbeIntervalDuration());

      try {
        Thread.sleep(1L);
      } catch (InterruptedException e) {
      }

      i += 1;
    }

    if (i == MAX_CONDITION_RETRIES) {
      fail("condition is not satisfied");
    }
  }
}

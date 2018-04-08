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
package io.zeebe.raft.state;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import io.zeebe.util.sched.clock.ActorClock;

public class Heartbeat
{
    private final int electionInterval;
    private long lastHeartbeat = 0;

    public Heartbeat(int electionInterval)
    {
        this.electionInterval = electionInterval;
    }

    public void updateLastHeartbeat()
    {
        lastHeartbeat = ActorClock.currentTimeMillis();
    }

    public boolean shouldElect()
    {
        return ActorClock.currentTimeMillis() >= (lastHeartbeat + electionInterval);
    }

    public Duration nextElectionTimeout()
    {
        return Duration.ofMillis(electionInterval + (Math.abs(ThreadLocalRandom.current().nextInt()) % electionInterval));
    }
}

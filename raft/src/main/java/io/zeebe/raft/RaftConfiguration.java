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
package io.zeebe.raft;

import java.time.Duration;

import io.zeebe.util.DurationUtil;

public class RaftConfiguration
{
    private String heartbeatInterval = "250ms";
    private String electionInterval = "1s";
    private String leaveTimeout = "1s";

    public String getHeartbeatInterval()
    {
        return heartbeatInterval;
    }

    public RaftConfiguration setHeartbeatInterval(final String heartbeatInterval)
    {
        this.heartbeatInterval = heartbeatInterval;
        return this;
    }

    public Duration getHeartbeatIntervalDuration() { return DurationUtil.parse(heartbeatInterval); }

    public String getElectionInterval()
    {
        return electionInterval;
    }

    public Duration getElectionIntervalDuration()
    {
        return DurationUtil.parse(electionInterval);
    }

    public RaftConfiguration setElectionInterval(final String electionInterval)
    {
        this.electionInterval = electionInterval;
        return this;
    }

    public String getLeaveTimeout()
    {
        return leaveTimeout;
    }

    public Duration getLeaveTimeoutDuration()
    {
        return DurationUtil.parse(leaveTimeout);
    }

    public RaftConfiguration setLeaveTimeout(String leaveTimeout)
    {
        this.leaveTimeout = leaveTimeout;
        return this;
    }

    @Override
    public String toString()
    {
        return "RaftConfiguration{" + "heartbeatIntervalMs=" + heartbeatInterval +
            ", electionIntervalMs=" + electionInterval + '}';
    }
}

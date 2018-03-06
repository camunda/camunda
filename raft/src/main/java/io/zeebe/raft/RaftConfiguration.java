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

public class RaftConfiguration
{
    protected int heartbeatIntervalMs = 250;
    protected int electionIntervalMs = 1000;
    protected int flushIntervalMs = 10;

    public int getHeartbeatIntervalMs()
    {
        return heartbeatIntervalMs;
    }

    public RaftConfiguration setHeartbeatIntervalMs(final int heartbeatIntervalMs)
    {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        return this;
    }

    public int getElectionIntervalMs()
    {
        return electionIntervalMs;
    }

    public RaftConfiguration setElectionIntervalMs(final int electionIntervalMs)
    {
        this.electionIntervalMs = electionIntervalMs;
        return this;
    }

    public int getFlushIntervalMs()
    {
        return flushIntervalMs;
    }

    public RaftConfiguration setFlushIntervalMs(final int flushIntervalMs)
    {
        this.flushIntervalMs = flushIntervalMs;
        return this;
    }

    @Override
    public String toString()
    {
        return "RaftConfiguration{" + "heartbeatIntervalMs=" + heartbeatIntervalMs +
            ", electionIntervalMs=" + electionIntervalMs +
            ", getFlushIntervalMs=" + flushIntervalMs + '}';
    }
}

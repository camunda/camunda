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

import java.util.Objects;

public class Transition
{

    private final RaftTranisiton raftTranisiton;
    private final int term;

    public Transition(RaftTranisiton raftTranisiton, int term)
    {
        this.raftTranisiton = raftTranisiton;
        this.term = term;
    }

    public RaftTranisiton getRaftTranisiton()
    {
        return raftTranisiton;
    }

    public int getTerm()
    {
        return term;
    }

    public boolean isValid(final RaftState state, final int currentTerm)
    {
        if (currentTerm > term)
        {
            return false;
        }

        switch (raftTranisiton)
        {
            case TO_CANDIDATE:
                return state == RaftState.FOLLOWER;
            case TO_LEADER:
                return state == RaftState.CANDIDATE;
            case TO_FOLLOWER:
            default:
                return true;
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        final Transition that = (Transition) o;
        return term == that.term &&
            raftTranisiton == that.raftTranisiton;
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(raftTranisiton, term);
    }

    @Override
    public String toString()
    {
        return "Transition{" +
            "raftTranisiton=" + raftTranisiton +
            ", term=" + term +
            '}';
    }
}

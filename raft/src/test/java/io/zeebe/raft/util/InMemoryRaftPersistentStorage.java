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
package io.zeebe.raft.util;

import java.util.ArrayList;
import java.util.List;

import io.zeebe.raft.RaftPersistentStorage;
import io.zeebe.transport.SocketAddress;

public class InMemoryRaftPersistentStorage implements RaftPersistentStorage
{

    private int term;
    private final SocketAddress votedFor = new SocketAddress();
    private final List<SocketAddress> members = new ArrayList<>();

    @Override
    public int getTerm()
    {
        return term;
    }

    @Override
    public InMemoryRaftPersistentStorage setTerm(final int term)
    {
        this.term = term;
        return this;
    }

    @Override
    public SocketAddress getVotedFor()
    {
        if (votedFor.hostLength() > 0)
        {
            return votedFor;
        }
        else
        {
            return null;
        }
    }

    @Override
    public InMemoryRaftPersistentStorage setVotedFor(final SocketAddress votedFor)
    {
        if (votedFor != null)
        {
            this.votedFor.wrap(votedFor);
        }
        else
        {
            this.votedFor.reset();
        }

        return this;
    }

    @Override
    public InMemoryRaftPersistentStorage addMember(final SocketAddress member)
    {
        members.add(member);
        return this;
    }

    public List<SocketAddress> getMembers()
    {
        return members;
    }

    @Override
    public InMemoryRaftPersistentStorage clearMembers()
    {
        members.clear();

        return this;
    }

    @Override
    public InMemoryRaftPersistentStorage save()
    {
        // do nothing
        return this;
    }
}

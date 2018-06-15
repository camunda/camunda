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

import static io.zeebe.util.EnsureUtil.ensureNotNull;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.raft.event.RaftConfigurationEventMember;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;

public class RaftMembers
{
    private final Map<SocketAddress, RaftMember> memberLookup = new HashMap<>();
    private final List<RaftMember> members = new ArrayList<>();
    private final RaftPersistentStorage persistentStorage;
    private final RaftMember localMember;
    private final Function<SocketAddress, RemoteAddress> remoteAddressResolver;

    public RaftMembers(SocketAddress localMember,
        RaftPersistentStorage persistentStorage,
        Function<SocketAddress, RemoteAddress> remoteAddressResolver)
    {
        this.persistentStorage = persistentStorage;
        this.remoteAddressResolver = remoteAddressResolver;
        this.localMember = new RaftMember(remoteAddressResolver.apply(localMember));
    }

    public List<RaftMember> getMemberList()
    {
        return members;
    }

    public List<SocketAddress> getMemberAddresses()
    {
        return members.stream()
            .map((rm) -> rm.getRemoteAddress().getAddress())
            .collect(Collectors.toList());
    }

    public int getMemberSize()
    {
        return members.size();
    }

    public RaftMember getMemberBySocketAddress(SocketAddress address)
    {
        return memberLookup.get(address);
    }

    public boolean hasMember(SocketAddress socketAddress)
    {
        return memberLookup.containsKey(socketAddress);
    }

    public void replaceMembersOnConfigurationChange(final ValueArray<RaftConfigurationEventMember> newMembers)
    {
        members.clear();
        memberLookup.clear();
        persistentStorage.clearMembers();

        final Iterator<RaftConfigurationEventMember> iterator = newMembers.iterator();
        while (iterator.hasNext())
        {
            addMember(iterator.next().getSocketAddress());
        }

        persistentStorage.save();
    }

    public void addMembersWhenJoined(final List<SocketAddress> membersToAdd)
    {
        membersToAdd.forEach(this::addMember);
        persistentStorage.save();
    }

    public RaftMember addMember(final SocketAddress socketAddress)
    {
        ensureNotNull("Raft node socket address", socketAddress);

        if (socketAddress.equals(localMember.getRemoteAddress().getAddress()))
        {
            return null;
        }

        if (!hasMember(socketAddress))
        {
            final RemoteAddress remoteAddress = remoteAddressResolver.apply(socketAddress);
            final RaftMember member = new RaftMember(remoteAddress);

            members.add(member);
            memberLookup.put(socketAddress, member);

            persistentStorage.addMember(socketAddress);

            return member;
        }
        else
        {
            return null;
        }

    }

    public RaftMember removeMember(final SocketAddress socketAddress)
    {
        ensureNotNull("Raft node socket address", socketAddress);

        if (socketAddress.equals(localMember.getRemoteAddress().getAddress()))
        {
            return null;
        }

        final RaftMember member = getMemberBySocketAddress(socketAddress);
        if (member != null)
        {
            members.remove(member);
            memberLookup.remove(socketAddress, member);
            persistentStorage.removeMember(socketAddress);
        }

        return member;
    }
}

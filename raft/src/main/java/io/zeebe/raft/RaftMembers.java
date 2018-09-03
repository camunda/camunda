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

import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.raft.event.RaftConfigurationEventMember;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RaftMembers {
  private final Map<Integer, RaftMember> memberLookup = new HashMap<>();
  private final List<RaftMember> members = new ArrayList<>();
  private final RaftPersistentStorage persistentStorage;
  private final RaftMember localMember;

  public RaftMembers(final int localNodeId, final RaftPersistentStorage persistentStorage) {
    this.persistentStorage = persistentStorage;
    this.localMember = new RaftMember(localNodeId);
  }

  public List<RaftMember> getMemberList() {
    return members;
  }

  public List<Integer> getMemberIds() {
    return members.stream().map(RaftMember::getNodeId).collect(Collectors.toList());
  }

  public int getMemberSize() {
    return members.size();
  }

  public RaftMember getMember(final int nodeId) {
    return memberLookup.get(nodeId);
  }

  public boolean hasMember(final int nodeId) {
    return memberLookup.containsKey(nodeId);
  }

  public void replaceMembersOnConfigurationChange(
      final ValueArray<RaftConfigurationEventMember> newMembers) {
    members.clear();
    memberLookup.clear();
    persistentStorage.clearMembers();

    for (final RaftConfigurationEventMember newMember : newMembers) {
      addMember(newMember.getNodeId());
    }

    persistentStorage.save();
  }

  public void addMembersWhenJoined(final List<Integer> membersToAdd) {
    membersToAdd.forEach(this::addMember);
    persistentStorage.save();
  }

  public RaftMember addMember(final int nodeId) {
    if (nodeId == localMember.getNodeId()) {
      return null;
    }

    if (!hasMember(nodeId)) {
      final RaftMember member = new RaftMember(nodeId);

      members.add(member);
      memberLookup.put(nodeId, member);

      persistentStorage.addMember(nodeId).save();

      return member;
    } else {
      return null;
    }
  }

  public RaftMember removeMember(final int nodeId) {
    if (nodeId == localMember.getNodeId()) {
      return null;
    }

    final RaftMember member = getMember(nodeId);
    if (member != null) {
      members.remove(member);
      memberLookup.remove(nodeId, member);
      persistentStorage.removeMember(nodeId).save();
    }

    return member;
  }
}

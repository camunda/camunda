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

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.raft.RaftPersistentStorage;
import java.util.ArrayList;
import java.util.List;

public class InMemoryRaftPersistentStorage implements RaftPersistentStorage {

  private final LogStream logStream;
  private Integer votedFor;
  private final List<Integer> members = new ArrayList<>();

  public InMemoryRaftPersistentStorage(final LogStream logStream) {
    this.logStream = logStream;
  }

  @Override
  public int getReplicationFactor() {
    // TODO: implement?
    return 0;
  }

  @Override
  public int getTerm() {
    return logStream.getTerm();
  }

  @Override
  public InMemoryRaftPersistentStorage setTerm(final int term) {
    this.logStream.setTerm(term);
    return this;
  }

  @Override
  public Integer getVotedFor() {
    return votedFor;
  }

  @Override
  public InMemoryRaftPersistentStorage setVotedFor(final Integer votedFor) {
    this.votedFor = votedFor;
    return this;
  }

  @Override
  public InMemoryRaftPersistentStorage addMember(final int nodeId) {
    members.add(nodeId);
    return this;
  }

  @Override
  public RaftPersistentStorage removeMember(final int nodeId) {
    members.removeIf(member -> member.equals(nodeId));
    return this;
  }

  public List<Integer> getMembers() {
    return members;
  }

  @Override
  public InMemoryRaftPersistentStorage clearMembers() {
    members.clear();

    return this;
  }

  @Override
  public InMemoryRaftPersistentStorage save() {
    // do nothing
    return this;
  }
}

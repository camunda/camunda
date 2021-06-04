/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.cluster;

import io.atomix.cluster.discovery.NodeDiscoveryService;
import io.atomix.cluster.protocol.GroupMembershipEvent;
import io.atomix.cluster.protocol.GroupMembershipEventListener;
import io.atomix.cluster.protocol.GroupMembershipProtocol;
import io.atomix.cluster.protocol.GroupMembershipProtocolConfig;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;

public final class TestGroupMembershipProtocol implements GroupMembershipProtocol {

  private final Set<Member> members = new CopyOnWriteArraySet<>();
  private final GroupMembershipProtocolConfig config;
  private final Set<GroupMembershipEventListener> listeners = new CopyOnWriteArraySet<>();

  public TestGroupMembershipProtocol() {
    this(new Config());
  }

  public TestGroupMembershipProtocol(final GroupMembershipProtocolConfig config) {
    this.config = config;
  }

  @Override
  public Set<Member> getMembers() {
    return members;
  }

  @Override
  public Member getMember(final MemberId memberId) {
    return members.stream().filter(m -> m.id().equals(memberId)).findFirst().orElse(null);
  }

  @Override
  public CompletableFuture<Void> join(
      final BootstrapService bootstrap,
      final NodeDiscoveryService discovery,
      final Member localMember) {
    members.add(localMember);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> leave(final Member localMember) {
    members.remove(localMember);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public GroupMembershipProtocolConfig config() {
    return config;
  }

  @Override
  public void addListener(final GroupMembershipEventListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(final GroupMembershipEventListener listener) {
    listeners.remove(listener);
  }

  public void sendEvent(final GroupMembershipEvent event) {
    listeners.forEach(listener -> listener.event(event));
  }

  public static class Config extends GroupMembershipProtocolConfig implements Type<Config> {

    @Override
    public Type getType() {
      return this;
    }

    @Override
    public GroupMembershipProtocol newProtocol(final Config config) {
      return new TestGroupMembershipProtocol(config);
    }

    @Override
    public Config newConfig() {
      return new Config();
    }

    @Override
    public String name() {
      return getClass().getCanonicalName();
    }
  }
}

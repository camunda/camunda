/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.session.impl;

import com.google.common.collect.Lists;
import io.atomix.cluster.MemberId;
import io.atomix.raft.session.CommunicationStrategy;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/** Cluster member selectors. */
public final class MemberSelectorManager {

  private final Set<MemberSelector> selectors = new CopyOnWriteArraySet<>();
  private final Set<Consumer<MemberId>> leaderChangeListeners = new CopyOnWriteArraySet<>();
  private volatile MemberId leader;
  private volatile Collection<MemberId> members = Collections.emptyList();

  /**
   * Adds a leader change listener.
   *
   * @param listener the listener to add
   */
  public void addLeaderChangeListener(final Consumer<MemberId> listener) {
    leaderChangeListeners.add(listener);
  }

  /**
   * Removes a leader change listener.
   *
   * @param listener the listener to remove
   */
  public void removeLeaderChangeListener(final Consumer<MemberId> listener) {
    leaderChangeListeners.remove(listener);
  }

  /**
   * Returns the current cluster leader.
   *
   * @return The current cluster leader.
   */
  public MemberId leader() {
    return leader;
  }

  /**
   * Returns the set of members in the cluster.
   *
   * @return The set of members in the cluster.
   */
  public Collection<MemberId> members() {
    return members;
  }

  /**
   * Creates a new address selector.
   *
   * @param selectionStrategy The server selection strategy.
   * @return A new address selector.
   */
  public MemberSelector createSelector(final CommunicationStrategy selectionStrategy) {
    final MemberSelector selector = new MemberSelector(leader, members, selectionStrategy, this);
    selectors.add(selector);
    return selector;
  }

  /** Resets all child selectors. */
  public void resetAll() {
    selectors.forEach(MemberSelector::reset);
  }

  /**
   * Resets all child selectors.
   *
   * @param leader The current cluster leader.
   * @param members The collection of all active members.
   */
  public void resetAll(final MemberId leader, final Collection<MemberId> members) {
    final MemberId oldLeader = this.leader;
    this.leader = leader;
    this.members = Lists.newLinkedList(members);
    selectors.forEach(s -> s.reset(leader, this.members));
    if (!Objects.equals(oldLeader, leader)) {
      leaderChangeListeners.forEach(l -> l.accept(leader));
    }
  }

  /**
   * Removes the given selector.
   *
   * @param selector The member selector to remove.
   */
  void remove(final MemberSelector selector) {
    selectors.remove(selector);
  }
}

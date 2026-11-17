/*
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
package io.atomix.raft.cluster;

import static java.util.Objects.requireNonNull;

import io.atomix.cluster.MemberId;

/**
 * An entry of the fence list of a Raft configuration. A member is fenced if its id and its
 * incarnation match an entry. Members reject requests from a fenced member, and a member that finds
 * itself fenced in a committed configuration stops taking part (see {@link
 * io.atomix.raft.FencedListener}).
 *
 * <p>Entries are never removed: a member that deletes its data and joins again gets a new
 * incarnation, so an old entry never matches it.
 *
 * @param memberId the fenced member
 * @param incarnation the incarnation of the member that is fenced
 */
public record FenceEntry(MemberId memberId, long incarnation) {

  public FenceEntry {
    requireNonNull(memberId, "memberId cannot be null");
  }

  public boolean matches(final MemberId id, final long incarnation) {
    return memberId.equals(id) && this.incarnation == incarnation;
  }
}

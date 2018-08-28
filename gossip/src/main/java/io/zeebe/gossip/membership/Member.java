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
package io.zeebe.gossip.membership;

import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.collection.Tuple;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.agrona.DirectBuffer;

public class Member {
  private final int id;
  private final GossipTerm term;

  private MembershipStatus status = MembershipStatus.ALIVE;

  private final List<Tuple<DirectBuffer, GossipTerm>> gossipTermByEventType = new ArrayList<>();

  public Member(final int id) {
    this.id = id;

    this.term = new GossipTerm().epoch(ActorClock.currentTimeMillis()).heartbeat(0);
  }

  public int getId() {
    return id;
  }

  public MembershipStatus getStatus() {
    return status;
  }

  public GossipTerm getTerm() {
    return term;
  }

  public Member setStatus(MembershipStatus status) {
    this.status = status;
    return this;
  }

  public Member setGossipTerm(GossipTerm term) {
    this.term.wrap(term);
    return this;
  }

  public GossipTerm getTermForEventType(DirectBuffer eventType) {
    for (Tuple<DirectBuffer, GossipTerm> tuple : gossipTermByEventType) {
      if (BufferUtil.equals(eventType, tuple.getLeft())) {
        return tuple.getRight();
      }
    }
    return null;
  }

  public void addTermForEventType(DirectBuffer type, GossipTerm gossipTerm) {
    final GossipTerm term = new GossipTerm().wrap(gossipTerm);
    final Tuple<DirectBuffer, GossipTerm> tuple = new Tuple<>(BufferUtil.cloneBuffer(type), term);
    gossipTermByEventType.add(tuple);
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("Member [id=");
    builder.append(id);
    builder.append(", status=");
    builder.append(status);
    builder.append(", term=");
    builder.append(term);
    builder.append("]");
    return builder.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Member member = (Member) o;
    return id == member.id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}

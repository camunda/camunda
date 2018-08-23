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
package io.zeebe.gossip.protocol;

import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.membership.GossipTerm;
import java.util.Objects;

public class MembershipEvent {
  private final GossipTerm gossipTerm = new GossipTerm();
  private long memberId;

  private MembershipEventType type = MembershipEventType.NULL_VAL;

  public MembershipEvent type(MembershipEventType type) {
    this.type = type;
    return this;
  }

  public MembershipEvent memberId(int memberId) {
    this.memberId = memberId;
    return this;
  }

  public MembershipEvent gossipTerm(GossipTerm term) {
    this.gossipTerm.wrap(term);
    return this;
  }

  public MembershipEventType getType() {
    return type;
  }

  public GossipTerm getGossipTerm() {
    return gossipTerm;
  }

  public int getMemberId() {
    return (int) memberId;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("MembershipEvent [memberId=");
    builder.append(memberId);
    builder.append(", type=");
    builder.append(type);
    builder.append(", gossipTerm=");
    builder.append(gossipTerm);
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
    final MembershipEvent that = (MembershipEvent) o;
    return memberId == that.memberId
        && Objects.equals(gossipTerm, that.gossipTerm)
        && type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(gossipTerm, memberId, type);
  }
}

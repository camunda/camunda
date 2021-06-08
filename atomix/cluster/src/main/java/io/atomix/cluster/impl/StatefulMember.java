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
package io.atomix.cluster.impl;

import io.atomix.cluster.Member;
import io.atomix.utils.Version;
import java.util.Objects;

/** Default cluster node. */
public final class StatefulMember extends Member {
  private final Version version;
  private volatile boolean active;
  private volatile boolean reachable;

  public StatefulMember(final Member member, final Version version) {
    super(
        member.id(),
        member.address(),
        member.zone(),
        member.rack(),
        member.host(),
        member.properties());
    this.version = version;
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), version);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    final StatefulMember that = (StatefulMember) o;
    return version.equals(that.version);
  }

  @Override
  public boolean isActive() {
    return active;
  }

  /**
   * Sets whether this member is an active member of the cluster.
   *
   * @param active whether this member is an active member of the cluster
   */
  void setActive(final boolean active) {
    this.active = active;
  }

  @Override
  public boolean isReachable() {
    return reachable;
  }

  @Override
  public Version version() {
    return version;
  }

  /**
   * Sets whether this member is reachable.
   *
   * @param reachable whether this member is reachable
   */
  void setReachable(final boolean reachable) {
    this.reachable = reachable;
  }
}

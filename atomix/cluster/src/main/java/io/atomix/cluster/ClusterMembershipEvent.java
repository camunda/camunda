/*
 * Copyright 2014-present Open Networking Foundation
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
package io.atomix.cluster;

import com.google.common.base.MoreObjects;
import io.atomix.utils.event.AbstractEvent;
import java.util.Objects;

/** Describes cluster-related event. */
public class ClusterMembershipEvent extends AbstractEvent<ClusterMembershipEvent.Type, Member> {

  /**
   * Creates an event of a given type and for the specified instance and the current time.
   *
   * @param type cluster event type
   * @param instance cluster device subject
   */
  public ClusterMembershipEvent(final Type type, final Member instance) {
    super(type, instance);
  }

  /**
   * Creates an event of a given type and for the specified device and time.
   *
   * @param type device event type
   * @param instance event device subject
   * @param time occurrence time
   */
  public ClusterMembershipEvent(final Type type, final Member instance, final long time) {
    super(type, instance, time);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type(), subject(), time());
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj instanceof ClusterMembershipEvent) {
      final ClusterMembershipEvent other = (ClusterMembershipEvent) obj;
      return Objects.equals(this.type(), other.type())
          && Objects.equals(this.subject(), other.subject())
          && Objects.equals(this.time(), other.time());
    }
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this.getClass())
        .add("type", type())
        .add("subject", subject())
        .add("time", time())
        .toString();
  }

  /** Type of cluster-related events. */
  public enum Type {
    /** Indicates that a new member has been added. */
    MEMBER_ADDED,

    /** Indicates that a member's metadata has changed. */
    METADATA_CHANGED,

    /** Indicates that a member's reachability has changed. */
    REACHABILITY_CHANGED,

    /** Indicates that a member has been removed. */
    MEMBER_REMOVED,
  }
}

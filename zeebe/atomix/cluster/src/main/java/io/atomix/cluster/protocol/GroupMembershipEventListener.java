/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.cluster.protocol;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.protocol.GroupMembershipEvent.Type;
import io.atomix.utils.event.EventListener;
import java.util.Set;

/** Node discovery event listener. */
public interface GroupMembershipEventListener extends EventListener<GroupMembershipEvent> {
  default void onState(final GroupMembershipState state) {}

  private static GroupMembershipEvent.Type convert(final ClusterMembershipEvent.Type tpe) {
    return switch (tpe) {
      case MEMBER_ADDED -> Type.MEMBER_ADDED;
      case MEMBER_REMOVED -> Type.MEMBER_REMOVED;
      case METADATA_CHANGED -> Type.METADATA_CHANGED;
      case REACHABILITY_CHANGED -> Type.REACHABILITY_CHANGED;
    };
  }

  record GroupMembershipState(Set<MemberId> members) {

    public void apply(final ClusterMembershipEvent event) {
      apply(new GroupMembershipEvent(convert(event.type()), event.subject()));
    }

    public void apply(final GroupMembershipEvent event) {
      switch (event.type()) {
        case MEMBER_ADDED -> members.add(event.member().id());
        case MEMBER_REMOVED -> members.remove(event.member().id());
        default -> {}
      }
    }
  }
}

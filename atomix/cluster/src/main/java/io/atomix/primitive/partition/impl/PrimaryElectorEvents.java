/*
 * Copyright 2017-present Open Networking Foundation
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
package io.atomix.primitive.partition.impl;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.event.EventType;
import io.atomix.primitive.partition.GroupMember;
import io.atomix.primitive.partition.MemberGroupId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PrimaryElectionEvent;
import io.atomix.primitive.partition.PrimaryTerm;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;

/** Leader elector events. */
public enum PrimaryElectorEvents implements EventType {
  CHANGE("change");

  public static final Namespace NAMESPACE =
      Namespace.builder()
          .nextId(Namespaces.BEGIN_USER_CUSTOM_ID + 50)
          .register(PrimaryElectionEvent.class)
          .register(PrimaryElectionEvent.Type.class)
          .register(PrimaryTerm.class)
          .register(GroupMember.class)
          .register(MemberId.class)
          .register(MemberGroupId.class)
          .register(PartitionId.class)
          .build(PrimaryElectorEvents.class.getSimpleName());
  private final String id;

  PrimaryElectorEvents(final String id) {
    this.id = id;
  }

  @Override
  public String id() {
    return id;
  }
}

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
package io.atomix.raft.storage.serializer;

import io.atomix.raft.cluster.RaftMember;

public class SerializerUtil {

  static MemberType getSBEType(final RaftMember.Type type) {
    return switch (type) {
      case ACTIVE -> MemberType.ACTIVE;
      case PASSIVE -> MemberType.PASSIVE;
      case INACTIVE -> MemberType.INACTIVE;
      case PROMOTABLE -> MemberType.PROMOTABLE;
    };
  }

  static RaftMember.Type getRaftMemberType(final MemberType type) {
    return switch (type) {
      case ACTIVE -> RaftMember.Type.ACTIVE;
      case PASSIVE -> RaftMember.Type.PASSIVE;
      case INACTIVE -> RaftMember.Type.INACTIVE;
      case PROMOTABLE -> RaftMember.Type.PROMOTABLE;
      default -> throw new IllegalStateException("Unexpected member type " + type);
    };
  }
}

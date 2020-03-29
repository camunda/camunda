/*
 * Copyright 2016-present Open Networking Foundation
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.operation.OperationId;
import io.atomix.primitive.operation.OperationType;
import io.atomix.primitive.partition.GroupMember;
import io.atomix.primitive.partition.MemberGroupId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;

/**
 * Primary elector operations.
 *
 * <p>WARNING: Do not refactor enum values. Only add to them. Changing values risk breaking the
 * ability to backup/restore/upgrade clusters.
 */
public enum PrimaryElectorOperations implements OperationId {
  ENTER(OperationType.COMMAND),
  GET_TERM(OperationType.QUERY);

  public static final Namespace NAMESPACE =
      Namespace.builder()
          .register(Namespaces.BASIC)
          .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
          .register(Enter.class)
          .register(GetTerm.class)
          .register(GroupMember.class)
          .register(MemberId.class)
          .register(MemberGroupId.class)
          .register(PartitionId.class)
          .build(PrimaryElectorOperations.class.getSimpleName());
  private final OperationType type;

  PrimaryElectorOperations(final OperationType type) {
    this.type = type;
  }

  @Override
  public String id() {
    return name();
  }

  @Override
  public OperationType type() {
    return type;
  }

  /** Abstract election query. */
  @SuppressWarnings("serial")
  public abstract static class ElectorOperation {}

  /** Abstract election topic query. */
  @SuppressWarnings("serial")
  public abstract static class PartitionOperation extends ElectorOperation {
    protected PartitionId partitionId;

    public PartitionOperation() {}

    public PartitionOperation(final PartitionId partitionId) {
      this.partitionId = checkNotNull(partitionId);
    }

    /**
     * Returns the partition ID.
     *
     * @return partition ID
     */
    public PartitionId partitionId() {
      return partitionId;
    }
  }

  /** GetTerm query. */
  @SuppressWarnings("serial")
  public static class GetTerm extends PartitionOperation {
    public GetTerm() {}

    public GetTerm(final PartitionId partitionId) {
      super(partitionId);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(getClass()).add("partition", partitionId).toString();
    }
  }

  /** Command for administratively changing the term state for a partition. */
  @SuppressWarnings("serial")
  public static class Enter extends PartitionOperation {
    private GroupMember member;

    Enter() {}

    Enter(final PartitionId partitionId, final GroupMember member) {
      super(partitionId);
      this.member = member;
    }

    /**
     * Returns the member.
     *
     * @return The member
     */
    public GroupMember member() {
      return member;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(getClass())
          .add("partition", partitionId)
          .add("member", member)
          .toString();
    }
  }
}

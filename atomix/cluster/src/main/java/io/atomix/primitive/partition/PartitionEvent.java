/*
 * Copyright 2019-present Open Networking Foundation
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
package io.atomix.primitive.partition;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import io.atomix.utils.event.AbstractEvent;
import io.atomix.utils.misc.TimestampPrinter;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/** Partition event. */
public class PartitionEvent extends AbstractEvent<PartitionEvent.Type, PartitionId> {

  private final Collection<MemberId> members;
  private final MemberId primary;
  private final Collection<MemberId> backups;

  public PartitionEvent(
      final Type type,
      final PartitionId partition,
      final Collection<MemberId> members,
      final MemberId primary,
      final Collection<MemberId> backups) {
    this(type, partition, members, primary, backups, System.currentTimeMillis());
  }

  public PartitionEvent(
      final Type type,
      final PartitionId partition,
      final Collection<MemberId> members,
      final MemberId primary,
      final Collection<MemberId> backups,
      final long time) {
    super(type, partition, time);
    this.members = checkNotNull(members);
    this.primary = primary;
    this.backups = checkNotNull(backups);
  }

  /**
   * Returns the partition ID.
   *
   * @return the partition ID
   */
  public PartitionId partitionId() {
    return subject();
  }

  /**
   * Returns the collection of partition members.
   *
   * @return the collection of partition members
   */
  public Collection<MemberId> members() {
    return members;
  }

  /**
   * Returns the current partition primary.
   *
   * @return the current partition primary
   */
  public Optional<MemberId> primary() {
    return Optional.ofNullable(primary);
  }

  /**
   * Returns the collection of backups.
   *
   * @return the collection of backups
   */
  public Collection<MemberId> backups() {
    return backups;
  }

  @Override
  public int hashCode() {
    return Objects.hash(partitionId(), members(), primary(), backups());
  }

  @Override
  public boolean equals(final Object object) {
    if (object instanceof PartitionEvent) {
      final PartitionEvent that = (PartitionEvent) object;
      return this.partitionId().equals(that.partitionId())
          && this.members.equals(that.members)
          && Objects.equals(this.primary, that.primary)
          && this.backups.equals(that.backups);
    }
    return false;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("time", new TimestampPrinter(time()))
        .add("type", type())
        .add("partitionId", subject())
        .add("members", members)
        .add("primary", primary)
        .add("backups", backups)
        .toString();
  }

  /** Partition event type. */
  public enum Type {
    /** Event type indicating the partition primary has changed. */
    PRIMARY_CHANGED,

    /** Event type indicating the partition backups have changed. */
    BACKUPS_CHANGED,

    /** Event type indicating the partition membership has changed. */
    MEMBERS_CHANGED,
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import io.atomix.cluster.MemberId;
import java.util.Collections;
import java.util.OptionalInt;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.jspecify.annotations.NullMarked;

/**
 * What an operation writes in the configuration it is applied to.
 *
 * <p>Two operations may run concurrently — that is, may lack a dependency edge between them — only
 * if their write sets are disjoint. Concurrent brokers learn each other's results by gossip, and
 * the members map merges entry by entry, so writes confined to distinct entries converge while
 * writes to anything shared race.
 *
 * <p><strong>A write set is not derivable from an operation's {@code memberId()}.</strong> That
 * field names the broker that <em>applies</em> the operation, which is not always the one written:
 * {@code MemberRemoveOperation} writes the entry of the member it removes, and {@code
 * PartitionForceReconfigureOperation} writes every member of its group. See {@link WriteSets}.
 *
 * <p>Note the deliberate limit of this type: it describes writes to the <em>configuration</em>. Six
 * of the existing operations write nothing at all and exist purely for their side effects elsewhere
 * in the broker, so disjointness cannot order them. That is why dependencies are declared rather
 * than inferred, and why this check is a safety net rather than the mechanism.
 *
 * @param members whose entries this operation writes; empty means it writes no member state
 * @param partitionId present when the write is confined to one partition of each named member
 * @param subConfigFields whether it writes sub-configuration-level state (routing state,
 *     incarnation number, tenant availability, partition distributor config), which conflicts with
 *     everything
 */
@NullMarked
public record WriteSet(
    SortedSet<MemberId> members, OptionalInt partitionId, boolean subConfigFields) {

  public WriteSet {
    members = Collections.unmodifiableSortedSet(new TreeSet<>(members));
  }

  /** Writes nothing in the configuration; ordering can only come from a declared dependency. */
  public static WriteSet none() {
    return new WriteSet(new TreeSet<>(), OptionalInt.empty(), false);
  }

  /** Writes one member's whole entry. */
  public static WriteSet member(final MemberId member) {
    return new WriteSet(new TreeSet<>(Set.of(member)), OptionalInt.empty(), false);
  }

  /** Writes several members' whole entries. */
  public static WriteSet members(final Set<MemberId> members) {
    return new WriteSet(new TreeSet<>(members), OptionalInt.empty(), false);
  }

  /** Writes one partition of one member. */
  public static WriteSet partition(final MemberId member, final int partitionId) {
    return new WriteSet(new TreeSet<>(Set.of(member)), OptionalInt.of(partitionId), false);
  }

  /** Writes one partition across several members. */
  public static WriteSet partition(final Set<MemberId> members, final int partitionId) {
    return new WriteSet(new TreeSet<>(members), OptionalInt.of(partitionId), false);
  }

  /** Writes sub-configuration-level state, so it conflicts with every other operation. */
  public static WriteSet subConfig() {
    return new WriteSet(new TreeSet<>(), OptionalInt.empty(), true);
  }

  /**
   * Whether these two operations can safely run at the same time.
   *
   * <p>Sub-configuration writes conflict with everything, including each other. Otherwise the sets
   * are disjoint when they name no member in common, or when they name the same member but are each
   * confined to a different partition of it.
   */
  public boolean isDisjointFrom(final WriteSet other) {
    if (subConfigFields || other.subConfigFields) {
      return false;
    }
    if (Collections.disjoint(members, other.members)) {
      return true;
    }
    return partitionId.isPresent()
        && other.partitionId.isPresent()
        && partitionId.getAsInt() != other.partitionId.getAsInt();
  }

  /** Whether this operation writes nothing at all in the configuration. */
  public boolean writesNothing() {
    return members.isEmpty() && !subConfigFields;
  }
}

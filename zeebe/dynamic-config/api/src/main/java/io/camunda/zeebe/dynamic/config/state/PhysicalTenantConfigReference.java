/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSortedMap;
import io.atomix.cluster.MemberId;
import java.util.Collection;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The configuration a physical tenant runs with, by reference: the cluster configuration holds the
 * version and the SHA-256 hash of the tenant's configuration, while every member keeps the
 * configuration itself on its own disk.
 *
 * <p>{@code version} is a counter of this tenant alone, independent of every version of the cluster
 * configuration, and is merged like {@link TenantAvailability}: the higher version wins, whichever
 * branch the merge of the enclosing {@link PartitionGroupConfiguration} takes. {@code
 * appliedVersions} records the version each member runs with. Each member only ever raises its own
 * entry, so it is merged per member by the higher value.
 *
 * @param version the version of the tenant's configuration, starting at {@link #INITIAL_VERSION}
 * @param sha256 the hex-encoded SHA-256 hash of the configuration of {@code version}
 * @param owner which input owns the tenant's configuration; it does not change after creation
 * @param incarnationId identifies this life of the tenant; a tenant created again after a removal
 *     would be a new incarnation
 * @param appliedVersions the version each member applied
 */
public record PhysicalTenantConfigReference(
    long version,
    String sha256,
    Owner owner,
    String incarnationId,
    SortedMap<MemberId, Long> appliedVersions) {

  public static final long INITIAL_VERSION = 1;

  public PhysicalTenantConfigReference {
    requireNonNull(sha256, "sha256 must not be null");
    requireNonNull(owner, "owner must not be null");
    requireNonNull(incarnationId, "incarnationId must not be null");
    requireNonNull(appliedVersions, "appliedVersions must not be null");
    appliedVersions = ImmutableSortedMap.copyOf(appliedVersions);
  }

  public static PhysicalTenantConfigReference initial(
      final String sha256, final Owner owner, final String incarnationId) {
    return new PhysicalTenantConfigReference(
        INITIAL_VERSION, sha256, owner, incarnationId, new TreeMap<>());
  }

  /** The next version of the configuration, with the given hash. */
  public PhysicalTenantConfigReference next(final String nextSha256) {
    return new PhysicalTenantConfigReference(
        version + 1, nextSha256, owner, incarnationId, appliedVersions);
  }

  /** Records that the member runs with the given version; a lower version than known is ignored. */
  public PhysicalTenantConfigReference withApplied(
      final MemberId memberId, final long appliedVersion) {
    if (appliedVersion(memberId) >= appliedVersion) {
      return this;
    }
    final var applied = new TreeMap<>(appliedVersions);
    applied.put(memberId, appliedVersion);
    return new PhysicalTenantConfigReference(version, sha256, owner, incarnationId, applied);
  }

  /** The version the member applied, or 0 if it applied none. */
  public long appliedVersion(final MemberId memberId) {
    return appliedVersions.getOrDefault(memberId, 0L);
  }

  /** Whether every given member applied the current version. */
  public boolean isAppliedBy(final Collection<MemberId> members) {
    return members.stream().allMatch(member -> appliedVersion(member) >= version);
  }

  PhysicalTenantConfigReference merge(final PhysicalTenantConfigReference other) {
    final var newer = version >= other.version ? this : other;
    final var applied = new TreeMap<>(appliedVersions);
    other.appliedVersions.forEach((member, applies) -> applied.merge(member, applies, Math::max));
    if (applied.equals(newer.appliedVersions)) {
      return newer;
    }
    return new PhysicalTenantConfigReference(
        newer.version, newer.sha256, newer.owner, newer.incarnationId, applied);
  }

  static Optional<PhysicalTenantConfigReference> merge(
      final Optional<PhysicalTenantConfigReference> left,
      final Optional<PhysicalTenantConfigReference> right) {
    return Stream.of(left, right)
        .flatMap(Optional::stream)
        .reduce(PhysicalTenantConfigReference::merge);
  }

  /** Which input owns the configuration of a physical tenant. */
  public enum Owner {
    /** The static configuration of the coordinator, read when it starts. */
    FILE,
    /** The management API. */
    API
  }
}

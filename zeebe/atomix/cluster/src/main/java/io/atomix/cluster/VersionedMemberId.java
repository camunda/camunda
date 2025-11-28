/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster;

/**
 * A MemberId that includes version information for dynamic node ID scenarios.
 *
 * <p>This class is used when nodes acquire their ID dynamically (e.g., from S3) and need to track
 * the version/lease generation of their ID. The version helps distinguish between different
 * instances that may have acquired the same node ID at different times.
 *
 * <p>When version information is not relevant (e.g., static node ID configuration), use {@link
 * MemberId} directly.
 */
public class VersionedMemberId extends MemberId {

  private final long idVersion;

  /**
   * Constructor for Kryo serialization. This is required because Kryo needs a no-arg constructor.
   */
  @SuppressWarnings("unused")
  private VersionedMemberId() {
    super("");
    idVersion = 0;
  }

  /**
   * Creates a new versioned member ID.
   *
   * @param id the member identifier string
   * @param idVersion the version of this member ID (must be > 0)
   */
  public VersionedMemberId(final String id, final long idVersion) {
    super(id);
    if (idVersion <= 0) {
      throw new IllegalArgumentException("idVersion must be > 0, was " + idVersion);
    }
    this.idVersion = idVersion;
  }

  /**
   * Returns the version of this member ID.
   *
   * @return the version number
   */
  @Override
  public long getIdVersion() {
    return idVersion;
  }

  /**
   * Returns hash code based only on the node id (not the version).
   *
   * <p>This is intentional to allow VersionedMemberId to be used interchangeably with MemberId in
   * HashMaps and other hash-based collections. The equals method is also designed to consider a
   * VersionedMemberId equal to a plain MemberId with the same node id.
   */
  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public boolean equals(final Object o) {
    if ((o instanceof final VersionedMemberId other)) {
      return super.equals(o) && idVersion == other.idVersion;
    } else if (o instanceof final MemberId other) {
      return super.equals(other);
    } else {
      return false;
    }
  }

  @Override
  public String toString() {
    return id() + "@v" + idVersion;
  }
}

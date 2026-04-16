/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

/**
 * Utility for composing and parsing broker member ids.
 *
 * <p>A member id identifies a broker globally across the cluster. In region-aware clusters it has
 * the form {@code "region-localId"} (e.g. {@code "us-east-1-0"}). In non-region-aware clusters it
 * is the plain integer node id as a string. The local node id is unique within a region but may
 * collide across regions, so the composite string is the only globally-unique identity.
 */
public final class MemberIds {

  private MemberIds() {}

  /**
   * Composes a member id from a region and a local node id.
   *
   * @param region the region name, or {@code null}/empty for non-region-aware clusters
   * @param localNodeId the node id, unique within the region
   * @return {@code "region-localNodeId"} if a region is set, otherwise just the local node id as a
   *     string
   */
  public static String compose(final String region, final int localNodeId) {
    return (region == null || region.isEmpty())
        ? Integer.toString(localNodeId)
        : region + "-" + localNodeId;
  }

  /**
   * Parses a composite member id into its region and local node id parts. The local node id is the
   * integer after the last dash; everything before it is the region. If the string contains no
   * dash, it is treated as a bare integer node id with no region.
   *
   * @param memberId the composite member id
   * @return the parsed parts; {@link Parsed#region()} is {@code null} when no region is present
   * @throws NumberFormatException if the local node id part is not an integer
   */
  public static Parsed parse(final String memberId) {
    final int lastDash = memberId.lastIndexOf('-');
    if (lastDash <= 0) {
      return new Parsed(null, Integer.parseInt(memberId));
    }
    return new Parsed(
        memberId.substring(0, lastDash), Integer.parseInt(memberId.substring(lastDash + 1)));
  }

  public record Parsed(String region, int localNodeId) {}
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.health;

/**
 * Immutable position of a node within the broker health tree.
 *
 * <p>A node's identity is the object <em>plus</em> this position: the {@link #name() display name}
 * need only be unique among its siblings, while the {@link #path()} and the {@link
 * #physicalTenant()} / {@link #partition()} tags locate the node in the tree. Positions are
 * composed from a parent's position via {@link #tenant(String)}, {@link #partition(int)} and {@link
 * #child(String)}, so each node carries everything a metric projection needs without ever walking
 * the tree.
 *
 * @param name the node's display name, unique only among its siblings
 * @param path the slash-joined chain of names from the root to this node
 * @param physicalTenant the physical tenant the node belongs to, or {@link #NONE}
 * @param partition the partition the node belongs to, or {@link #NONE}
 */
public record HealthNodePosition(
    String name, String path, String physicalTenant, String partition) {

  /** Tag value used when a node is not associated with a specific physical tenant or partition. */
  public static final String NONE = "none";

  /** The root broker node ({@code Broker-<id>}), associated with no tenant or partition. */
  public static HealthNodePosition broker(final String brokerName) {
    return new HealthNodePosition(brokerName, brokerName, NONE, NONE);
  }

  /** A physical-tenant node ({@code Tenant-<id>}) below this node. */
  public HealthNodePosition tenant(final String physicalTenantId) {
    final var tenantName = "Tenant-" + physicalTenantId;
    return new HealthNodePosition(tenantName, path + "/" + tenantName, physicalTenantId, NONE);
  }

  /** A partition node ({@code Partition-<n>}) below this node, inheriting the physical tenant. */
  public HealthNodePosition partition(final int partitionId) {
    final var partitionName = "Partition-" + partitionId;
    return new HealthNodePosition(
        partitionName, path + "/" + partitionName, physicalTenant, String.valueOf(partitionId));
  }

  /** A leaf node below this node, inheriting both the physical tenant and the partition. */
  public HealthNodePosition child(final String childName) {
    return new HealthNodePosition(childName, path + "/" + childName, physicalTenant, partition);
  }
}

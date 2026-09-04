/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import org.jspecify.annotations.NullMarked;

/**
 * The partition count one physical tenant should reach, and the tenant it applies to.
 *
 * <p>The two travel together because neither says anything on its own. The partition count is the
 * only dimension of a scaling request that has a tenant dimension — cluster membership and the
 * replication factor are cluster-wide — so a request that leaves the partition count alone names no
 * tenant at all, and one that changes it has to name the tenant it grows. Pairing them keeps a
 * planner from having to invent a tenant for a request that scales none, which is what a default of
 * {@code "default"} amounted to.
 *
 * @param physicalTenantId the tenant whose partition group grows. Must exist in the configuration
 *     being planned against; the request transformer resolves it and rejects an unknown one,
 *     because only it can say what the request named.
 * @param partitionCount the count that tenant's partition group should reach. Partitions can only
 *     be scaled up, so a count below the tenant's current one is rejected during planning.
 */
@NullMarked
public record TenantPartitionCount(String physicalTenantId, int partitionCount) {}

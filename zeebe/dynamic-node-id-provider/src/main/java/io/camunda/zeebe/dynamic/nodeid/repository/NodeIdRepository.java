/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid.repository;

import io.camunda.zeebe.dynamic.nodeid.Lease;

public interface NodeIdRepository extends AutoCloseable {

  /**
   * Initialize the leases if they haven't been created yet.
   *
   * @param count the number of leases to create
   */
  void initialize(int count);

  /**
   * Get a lease without acquiring it. This can be used to verify the liveness of other nodes or
   * their view of the cluster
   *
   * @param nodeId the node to get the lease for
   * @return the lease
   */
  StoredLease getLease(int nodeId);

  /**
   * Acquire a lease, if it matches the provided {@param previousETag}.
   *
   * @param lease the lease to store
   * @param previousETag the eTag of the current lease in the store
   * @return
   *     <ul>
   *       <li>null if the lease could not be acquired
   *       <li>the lease if it was acquired correctly.
   *     </ul>
   */
  StoredLease.Initialized acquire(Lease lease, String previousETag);

  /**
   * Gracefully release the lease in the store
   *
   * @param lease the lease to release
   */
  void release(StoredLease.Initialized lease);

  sealed interface StoredLease {
    default boolean isInitialized() {
      return switch (this) {
        case final Uninitialized u -> false;
        case final Initialized i -> true;
      };
    }

    String eTag();

    record Uninitialized(String eTag) implements StoredLease {}

    record Initialized(Metadata metadata, Lease lease, String eTag) implements StoredLease {}
  }
}

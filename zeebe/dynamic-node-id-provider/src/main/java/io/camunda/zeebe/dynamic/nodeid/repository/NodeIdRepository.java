/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid.repository;

import io.camunda.zeebe.dynamic.nodeid.Lease;
import io.camunda.zeebe.dynamic.nodeid.NodeInstance;
import java.util.Objects;

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
   * Acquire a lease, if it matches the provided {@param previousETag}. This method can be used both
   * for the initial acquire and for renewing an existing lease.
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

    static StoredLease of(
        final int nodeId, final Lease lease, final Metadata metadata, final String eTag) {
      if (eTag == null || eTag.isEmpty()) {
        throw new IllegalArgumentException("eTag cannot be null or empty:" + eTag);
      }
      if (lease == null || metadata == null) {
        return new StoredLease.Uninitialized(new NodeInstance(nodeId), eTag);
      } else {
        return new StoredLease.Initialized(metadata, lease, eTag);
      }
    }

    String eTag();

    record Uninitialized(NodeInstance node, String eTag) implements StoredLease {
      public Uninitialized {
        Objects.requireNonNull(eTag, "ETag cannot be null");
        if (eTag.isEmpty()) {
          throw new IllegalArgumentException("eTag cannot be empty");
        }
      }
    }

    record Initialized(Metadata metadata, Lease lease, String eTag) implements StoredLease {
      public Initialized {
        Objects.requireNonNull(metadata, "Metadata cannot be null");
        Objects.requireNonNull(lease, "Lease cannot be null");
        Objects.requireNonNull(eTag, "ETag cannot be null");
        if (eTag.isEmpty()) {
          throw new IllegalArgumentException("eTag cannot be empty");
        }
      }
    }
  }
}

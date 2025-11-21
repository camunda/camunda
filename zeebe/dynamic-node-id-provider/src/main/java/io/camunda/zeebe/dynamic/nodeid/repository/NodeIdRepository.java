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
import io.camunda.zeebe.dynamic.nodeid.Version;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Objects;
import java.util.Optional;

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

  /**
   * A StoredLease represents the Lease stored in a Repository such as S3. It can be
   *
   * <ul>
   *   <li>{@link Uninitialized} when it's first created or when it's gracefully released by the
   *       node holding it
   *   <li>{@link Initialized} when it's held by another node. Note that Initialized does not
   *       guarantee validity, since a node can expire. This can happen if the lease is failed to be
   *       renewed in time, either due to the node crashing or failing in some way.
   * </ul>
   */
  sealed interface StoredLease {
    NodeInstance node();

    String eTag();

    default Version version() {
      return node().version();
    }

    default boolean isInitialized() {
      return switch (this) {
        case final Uninitialized u -> false;
        case final Initialized i -> true;
      };
    }

    default boolean isStillValid(long now) {
      return switch (this) {
        case final Uninitialized u -> false;
        case final Initialized i -> i.lease().isStillValid(now);
      };
    }

    /** Acquire the initial lease * */
    default Lease acquireInitialLease(String taskId, InstantSource clock, Duration leaseDuration) {
      if (isStillValid(clock.millis())) {
        return null;
      } else {
        return Lease.from(taskId, clock.millis() + leaseDuration.toMillis(), node());
      }
    }

    static StoredLease of(
        final int nodeId, final Lease lease, final Metadata metadata, final String eTag) {
      if (eTag == null || eTag.isEmpty()) {
        throw new IllegalArgumentException("eTag cannot be null or empty:" + eTag);
      }
      if (lease == null) {
        var version = Optional.ofNullable(metadata).map(Metadata::version).orElse(new Version(0));
        return new StoredLease.Uninitialized(new NodeInstance(nodeId, version), eTag);
      } else {
        return new StoredLease.Initialized(metadata, lease, eTag);
      }
    }

    record Uninitialized(NodeInstance node, String eTag) implements StoredLease {
      public Uninitialized {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(eTag, "eTag cannot be null");
        if (eTag.isEmpty()) {
          throw new IllegalArgumentException("eTag cannot be empty");
        }
      }
    }

    record Initialized(Metadata metadata, Lease lease, String eTag) implements StoredLease {

      public Initialized(Metadata metadata, int nodeId, String eTag) {
        this(metadata, Lease.fromMetadata(metadata, nodeId), eTag);
      }

      public Initialized {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        Objects.requireNonNull(lease.nodeInstance(), "node cannot be null");
        Objects.requireNonNull(eTag, "eTag cannot be null");
        if (eTag.isEmpty()) {
          throw new IllegalArgumentException("eTag cannot be empty");
        }
        Objects.requireNonNull(lease, "Lease cannot be null");
      }

      @Override
      public NodeInstance node() {
        return lease.nodeInstance();
      }
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import java.util.Map;
import java.util.Optional;

/**
 * The base of every cluster metadata store error. Unchecked, so {@link ClusterMetadataStore#watch}
 * can throw it without a throws clause. A backing mechanism can add errors of its own that extend
 * it.
 */
public abstract class ClusterMetadataStoreException extends RuntimeException {

  protected ClusterMetadataStoreException(final String message) {
    super(message);
  }

  protected ClusterMetadataStoreException(final String message, final Throwable cause) {
    super(message, cause);
  }

  /** The namespace was never created or was deleted. */
  public static class NamespaceNotFoundException extends ClusterMetadataStoreException {

    public NamespaceNotFoundException(final String message) {
      super(message);
    }
  }

  /** The namespace to create already exists. */
  public static class NamespaceAlreadyExistsException extends ClusterMetadataStoreException {

    public NamespaceAlreadyExistsException(final String message) {
      super(message);
    }
  }

  /** A write request failed, for example because the writer changed before acknowledgement. */
  public static class WriteFailedException extends ClusterMetadataStoreException {

    public WriteFailedException(final String message) {
      super(message);
    }

    public WriteFailedException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  /** The write request timed out; the write may already have committed. */
  public static class WriteTimeoutException extends WriteFailedException {

    public WriteTimeoutException(final String message) {
      super(message);
    }

    public WriteTimeoutException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * The condition of a write did not hold, or, in a batch, any condition; nothing was applied. It
   * carries each key whose condition failed with its value as {@link ClusterMetadataStore#get}
   * returns it now, so the caller can retry without another read.
   */
  public static class ConditionFailedException extends ClusterMetadataStoreException {

    private final transient Map<StoreKey, Optional<KeyValue>> current;

    public ConditionFailedException(
        final String message, final Map<StoreKey, Optional<KeyValue>> current) {
      super(message);
      this.current = Map.copyOf(current);
    }

    /** Each key whose condition failed, with its current value. */
    public Map<StoreKey, Optional<KeyValue>> current() {
      return current;
    }
  }

  /** A key, namespace name or batch breaks a rule that needs no store state. */
  public static class InvalidArgumentException extends ClusterMetadataStoreException {

    public InvalidArgumentException(final String message) {
      super(message);
    }
  }

  /** This broker's view can no longer follow the store, so nothing can be watched. */
  public static class ViewStalledException extends ClusterMetadataStoreException {

    public ViewStalledException(final String message) {
      super(message);
    }
  }

  /** The store was closed, for example because the broker shuts down. */
  public static class StoreClosedException extends ClusterMetadataStoreException {

    public StoreClosedException(final String message) {
      super(message);
    }

    public StoreClosedException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}

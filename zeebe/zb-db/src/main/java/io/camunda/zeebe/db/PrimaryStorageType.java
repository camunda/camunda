/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

/**
 * The storage backend of a partition's primary state, as configured by {@code
 * camunda.data.primary-storage.type}. The on-disk state is backend specific, a partition written by
 * one backend cannot be opened by the other.
 */
public enum PrimaryStorageType {
  ROCKSDB,
  SQLITE;

  /** The value used in configuration and messages, e.g. {@code rocksdb}. */
  public String configValue() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup;

import io.camunda.zeebe.db.rdbms.RdbmsStorage;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The relational database that holds the state of one partition group's partitions, when {@code
 * camunda.data.primary-storage.type} is {@code rdbms}. {@link #open()} creates or updates the
 * schema before the partitions start. The connection pool belongs to the application, which closes
 * it after the broker has stopped.
 */
@NullMarked
public final class RdbmsStateStorage implements AutoCloseable {

  private final String partitionGroup;
  private final RdbmsStateDatabase database;
  private volatile @Nullable RdbmsStorage storage;

  public RdbmsStateStorage(final String partitionGroup, final RdbmsStateDatabase database) {
    this.partitionGroup = Objects.requireNonNull(partitionGroup, "must specify a partition group");
    this.database = Objects.requireNonNull(database, "must specify the database");
  }

  /**
   * Creates or updates the schema.
   *
   * @throws RuntimeException if the database can't be reached or its schema can't be updated
   */
  public void open() {
    storage = RdbmsStorage.open(database.dataSource(), database.prefix());
  }

  /**
   * @throws IllegalStateException if it isn't open
   */
  public RdbmsStorage storage() {
    final var opened = storage;
    if (opened == null) {
      throw new IllegalStateException(
          "Expected the database of partition group '%s' to be open, but it isn't"
              .formatted(partitionGroup));
    }
    return opened;
  }

  @Override
  public void close() {
    storage = null;
  }
}

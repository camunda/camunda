/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.contract;

import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.rdbms.RdbmsStorage;
import io.camunda.zeebe.db.rdbms.RdbmsZeebeDbFactory;
import io.camunda.zeebe.db.rdbms.StateIdentity;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.h2.jdbcx.JdbcDataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The relational storage on one database per JVM, with the consistency checks on. Every factory
 * gets a partition and so a state of its own, so tests never see each other's rows, as with
 * RocksDB's directories.
 */
final class RdbmsTestFactory {

  private static final AtomicInteger NEXT_PARTITION_ID = new AtomicInteger();

  private RdbmsTestFactory() {}

  static <ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<ColumnFamilyType> create() {
    return create(Database.H2);
  }

  static <ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<ColumnFamilyType> create(final Database database) {
    return create(database, nextPartition(), () -> 1);
  }

  /** A factory for a partition that other factories may share, for example after a failover. */
  static <ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<ColumnFamilyType> create(
          final Database database, final StateIdentity partition, final LongSupplier term) {
    return new RdbmsZeebeDbFactory<>(
        database.storage(),
        partition,
        term,
        new ConsistencyChecksSettings(true, true),
        SimpleMeterRegistry::new);
  }

  /** Returns a partition that no other factory has used yet. */
  static StateIdentity nextPartition() {
    return new StateIdentity("contract-tests", NEXT_PARTITION_ID.incrementAndGet());
  }

  enum Database {
    /** In memory, for the unit tests. */
    H2,
    /** In a container that starts with the first test using it, for the integration tests. */
    POSTGRESQL;

    private RdbmsStorage storage() {
      return switch (this) {
        case H2 -> H2Storage.STORAGE;
        case POSTGRESQL -> PostgresStorage.STORAGE;
      };
    }
  }

  private static final class H2Storage {

    private static final RdbmsStorage STORAGE = open();

    private static RdbmsStorage open() {
      final var dataSource = new JdbcDataSource();
      dataSource.setURL("jdbc:h2:mem:zeebe-db-contract-tests;DB_CLOSE_DELAY=-1");
      return RdbmsStorage.open(dataSource, "");
    }
  }

  private static final class PostgresStorage {

    private static final RdbmsStorage STORAGE = open();

    private static RdbmsStorage open() {
      final var container = new PostgreSQLContainer("postgres:15-alpine");
      container.start();
      final var dataSource = new PGSimpleDataSource();
      dataSource.setUrl(container.getJdbcUrl());
      dataSource.setUser(container.getUsername());
      dataSource.setPassword(container.getPassword());
      return RdbmsStorage.open(dataSource, "");
    }
  }
}

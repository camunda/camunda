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
import org.h2.jdbcx.JdbcDataSource;

/**
 * The relational storage on one in-memory H2 database, with the consistency checks on. Every
 * factory gets a partition and so a state of its own, so tests never see each other's rows, as with
 * RocksDB's directories.
 */
final class RdbmsTestFactory {

  private static final AtomicInteger NEXT_PARTITION_ID = new AtomicInteger();
  private static final RdbmsStorage STORAGE = openStorage();

  private RdbmsTestFactory() {}

  static <ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<ColumnFamilyType> create() {
    return new RdbmsZeebeDbFactory<>(
        STORAGE,
        new StateIdentity("contract-tests", NEXT_PARTITION_ID.incrementAndGet()),
        () -> 1,
        new ConsistencyChecksSettings(true, true),
        SimpleMeterRegistry::new);
  }

  private static RdbmsStorage openStorage() {
    final var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:zeebe-db-contract-tests;DB_CLOSE_DELAY=-1");
    return RdbmsStorage.open(dataSource, "");
  }
}

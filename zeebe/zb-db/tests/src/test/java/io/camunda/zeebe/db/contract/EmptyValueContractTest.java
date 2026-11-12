/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A value that serializes to no bytes. RocksDB's {@code get(key)} and {@code exists} read it as
 * missing, while {@code get(key, supplier)} and the consistency checks see it. Every implementation
 * has to behave the same, so that it can replace RocksDB. Scans aren't covered: RocksDB fails to
 * visit an empty value.
 */
public abstract class EmptyValueContractTest {

  @TempDir private Path directory;
  private ZeebeDb<DefaultColumnFamily> db;
  private ColumnFamily<DbLong, DbBytes> columnFamily;

  protected abstract ZeebeDbFactory<DefaultColumnFamily> createFactory();

  @BeforeEach
  void setUp() {
    db = createFactory().createDb(directory.resolve("runtime").toFile());
    columnFamily =
        db.createColumnFamily(
            DefaultColumnFamily.DEFAULT, db.createContext(), new DbLong(), new DbBytes());
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldReadAnEmptyValueOnlyWithAValueSupplier() {
    // given
    columnFamily.upsert(dbLong(1), emptyValue());

    // when - then
    assertThat(columnFamily.get(dbLong(1))).isNull();
    assertThat(columnFamily.exists(dbLong(1))).isFalse();
    assertThat(columnFamily.get(dbLong(1), DbBytes::new))
        .isNotNull()
        .extracting(DbBytes::getLength)
        .isEqualTo(0);
  }

  @Test
  void shouldCountAnEmptyValueAsExistingInTheConsistencyChecks() {
    // given
    columnFamily.insert(dbLong(1), emptyValue());

    // when - then
    assertThatThrownBy(() -> columnFamily.insert(dbLong(1), emptyValue()))
        .isInstanceOf(ZeebeDbInconsistentException.class);
    assertThatCode(() -> columnFamily.update(dbLong(1), emptyValue())).doesNotThrowAnyException();
    assertThatCode(() -> columnFamily.deleteExisting(dbLong(1))).doesNotThrowAnyException();
  }

  protected ZeebeDb<DefaultColumnFamily> db() {
    return db;
  }

  protected ColumnFamily<DbLong, DbBytes> columnFamily() {
    return columnFamily;
  }

  protected Path directory() {
    return directory;
  }

  protected static DbLong dbLong(final long value) {
    final var dbLong = new DbLong();
    dbLong.wrapLong(value);
    return dbLong;
  }

  protected static DbBytes emptyValue() {
    final var value = new DbBytes();
    value.wrapBytes(new byte[0]);
    return value;
  }
}

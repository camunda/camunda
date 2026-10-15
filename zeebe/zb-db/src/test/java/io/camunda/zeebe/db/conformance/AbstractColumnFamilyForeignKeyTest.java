/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.conformance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import org.junit.Test;

/**
 * Foreign-key enforcement conformance tests for {@link io.camunda.zeebe.db.ColumnFamily}. These are
 * point operations, but they depend on {@code DbForeignKey} support, which is separate from the
 * rest of the point-operation contract.
 */
public abstract class AbstractColumnFamilyForeignKeyTest extends AbstractColumnFamilyTestBase {

  @Test
  public void shouldThrowOnMissingForeignKeyInKeyPosition() {
    // given
    key.wrapLong(1);
    final var foreignKey = new DbForeignKey<>(key, DefaultColumnFamily.DEFAULT);
    final var value = new DbLong();
    final var columnFamilyWithForeignKey =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), foreignKey, value);

    // then
    assertThatThrownBy(() -> columnFamilyWithForeignKey.insert(foreignKey, value))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("Foreign key");
  }

  @Test
  public void shouldThrowOnMissingForeignKeyInValuePosition() {
    // given
    key.wrapLong(1);
    final var foreignKey = new DbForeignKey<>(new DbLong(), DefaultColumnFamily.DEFAULT);
    final var columnFamilyWithForeignKey =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), key, foreignKey);
    // then
    assertThatThrownBy(() -> columnFamilyWithForeignKey.insert(key, foreignKey))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("Foreign key");
  }
}
